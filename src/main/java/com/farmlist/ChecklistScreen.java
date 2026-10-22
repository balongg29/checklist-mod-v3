package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One checklist: rows with item icons and real check boxes, scan, copy, add items, complete, and delete.
 * Takes the file name, e.g. "Wheat Farm.txt".
 */
public class ChecklistScreen extends Screen {

    private static final int ROW_H = 26;      // row spacing; the row itself is ROW_H - 2 tall
    private static final int HEADER_H = 88;   // title, two button rows, progress bar
    private static final int FOOTER_H = 22;   // status line
    private static final int PENCIL = 16;

    private record Row(String id, int count) {
    }

    private final String fileName;
    private boolean confirmingDelete = false;

    private Progress progress;
    private final List<Row> rows = new ArrayList<>();
    private int scroll = 0;
    private boolean dragging = false;

    private int px, py, pw, ph;               // the panel
    private int listX, listY, listW, listH;   // the scrolling list inside it

    public ChecklistScreen(String fileName) {
        super(Component.literal("Checklist"));
        this.fileName = fileName;
    }

    @Override
    protected void init() {
        layout();
        loadRows();
        rebuild();
    }

    private void layout() {
        pw = Math.min(this.width - 16, 320);
        ph = Math.min(this.height - 16, 340);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        listX = px + 8;
        listW = pw - 16 - Ui.SB_W - 2;        // list + scroll bar fill the panel evenly, 8px margin each side
        listY = py + HEADER_H;
        listH = ph - HEADER_H - FOOTER_H;
    }

    private void loadRows() {
        progress = new Progress(baseName());
        rows.clear();
        FarmListClient.Parsed parsed = FarmListClient.parseFile(
                FarmListClient.TRANSCRIPTION_DIR.resolve(fileName));
        for (Map.Entry<String, Integer> e : parsed.rows().entrySet()) {
            rows.add(new Row(e.getKey(), e.getValue()));
        }
        if (!parsed.rejected().isEmpty() && FarmListClient.status.isEmpty()) {
            FarmListClient.message(parsed.rejected().size() + " line(s) in this file are not real items and are hidden.");
        }
        clampScroll();
    }

    /** Rebuilds the real buttons (the list itself is drawn by hand). */
    private void rebuild() {
        clearWidgets();
        int bx = px + 8;
        int bw = pw - 16;

        int w4 = (bw - 3 * 4) / 4;
        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                this.minecraft.gui.setScreen(new PickerScreen())
        ).bounds(bx, py + 20, w4, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Scan"), b ->
                scanInventory()
        ).bounds(bx + (w4 + 4), py + 20, w4, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Add Item"), b ->
                this.minecraft.gui.setScreen(new CatalogScreen(fileName))
        ).bounds(bx + 2 * (w4 + 4), py + 20, w4, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Copy"), b ->
                FarmListClient.copyToClipboard(fileName)
        ).bounds(bx + 3 * (w4 + 4), py + 20, w4, 20).build());

        int w2 = (bw - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal(progress.isComplete() ? "Reopen" : "Complete"), b -> {
            progress.setComplete(!progress.isComplete());
            FarmListClient.message(progress.isComplete() ? "Marked complete." : "Reopened.");
            rebuild();
        }).bounds(bx, py + 44, w2, 20).build());

        addRenderableWidget(Button.builder(Component.literal(confirmingDelete ? "Confirm delete" : "Delete"), b -> {
            if (!confirmingDelete) {
                confirmingDelete = true;
                FarmListClient.message("Press Confirm delete to remove this checklist.");
                rebuild();
            } else {
                FarmListClient.deleteChecklist(fileName);
                this.minecraft.gui.setScreen(new PickerScreen());
            }
        }).bounds(bx + w2 + 4, py + 44, w2, 20).build());
    }

    private String baseName() {
        return FarmListClient.baseName(fileName);
    }

    private void scanInventory() {
        Map<String, Integer> have = FarmListClient.countInventory();
        int newlyChecked = 0;
        for (Row r : rows) {
            if (FarmListClient.isPlaceholder(r.id()) && FarmListClient.entryFor(
                    FarmListClient.countedId(r.id(), progress)) == null) {
                continue;   // no item chosen yet, nothing to count
            }
            String counted = FarmListClient.countedId(r.id(), progress);
            int found = have.getOrDefault(counted, 0);
            if (progress.isManual(r.id()) && progress.getHave(r.id()) != null) {
                found = Math.max(found, progress.getHave(r.id()));   // keep what you typed in by hand (chests etc.)
            }
            progress.setHave(r.id(), found);   // remembered so the row can show e.g. 56/268
            if (!progress.isChecked(r.id()) && found >= r.count()) {
                progress.set(r.id(), true);
                newlyChecked++;
            }
        }
        FarmListClient.message(newlyChecked == 0
                ? "Scanned your inventory: nothing new to check off."
                : "Scanned your inventory: checked off " + newlyChecked + (newlyChecked == 1 ? " item." : " items."));
    }

    // ---------- scrolling ----------

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_H - listH);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int rowY(int index) {
        return listY + index * ROW_H - scroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Ui.in((int) mouseX, (int) mouseY, px, listY, pw, listH)) {
            scroll -= (int) (scrollY * ROW_H);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int barX() {
        return listX + listW + 2;
    }

    // ---------- clicking ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (event.button() == 0 && Ui.in(mx, my, barX(), listY, Ui.SB_W, listH)) {
            dragging = true;
            scroll = Ui.scrollFromY(my, listY, listH, rows.size() * ROW_H, listH);
            return true;
        }
        if (event.button() == 0 && Ui.in(mx, my, listX, listY, listW, listH)) {
            int index = (my - listY + scroll) / ROW_H;
            if (index >= 0 && index < rows.size()) {
                int ry = rowY(index);
                if (my - ry < ROW_H - 2) {   // ignore the little gap between rows
                    Row r = rows.get(index);
                    if (Ui.in(mx, my, listX + listW - PENCIL - 5, ry + 4, PENCIL, PENCIL)) {
                        this.minecraft.gui.setScreen(new CatalogScreen(fileName, r.id()));
                        return true;
                    }
                    if (FarmListClient.isPlaceholder(r.id()) && FarmListClient.entryFor(
                            FarmListClient.countedId(r.id(), progress)) == null) {
                        this.minecraft.gui.setScreen(new CatalogScreen(fileName, r.id()));   // nothing to tick yet
                        return true;
                    }
                    progress.set(r.id(), !progress.isChecked(r.id()));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            scroll = Ui.scrollFromY((int) event.y(), listY, listH, rows.size() * ROW_H, listH);
            clampScroll();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    // ---------- drawing ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);   // the buttons

        // title
        String title = Ui.fit(this.font, baseName(), pw - 20);
        Ui.textCenter(g, this.font, title, px + pw / 2, py + 7, Ui.TEXT_DARK, false);

        // progress bar
        int done = 0;
        for (Row r : rows) {
            if (progress.isChecked(r.id())) done++;
        }
        int barX = px + 8;
        int barW = pw - 16;
        Ui.inset(g, barX, py + 70, barW, 12, 0xFF373737);
        if (!rows.isEmpty()) {
            int fillW = (barW - 2) * done / rows.size();
            g.fill(barX + 1, py + 71, barX + 1 + fillW, py + 81, progress.isComplete() ? Ui.GREEN : 0xFF3FA34D);
        }
        Ui.textCenter(g, this.font, done + "/" + rows.size() + (progress.isComplete() ? "  complete" : ""),
                px + pw / 2, py + 72, Ui.WHITE, true);

        // the rows
        if (rows.isEmpty()) {
            Ui.textCenter(g, this.font, "Nothing here yet. Press Add Item.", px + pw / 2, listY + 16, Ui.TEXT_DARK, false);
        }
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < rows.size(); i++) {
            int ry = rowY(i);
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            drawRow(g, rows.get(i), listX, ry, listW, mouseX, mouseY);
        }
        g.disableScissor();
        Ui.scrollbar(g, barX(), listY, listH, scroll, rows.size() * ROW_H, listH);

        // status line
        if (!FarmListClient.status.isEmpty()) {
            Ui.text(g, this.font, Ui.fit(this.font, FarmListClient.status, pw - 16),
                    px + 8, py + ph - 15, Ui.TEXT_DARK, false);
        }
    }

    private void drawRow(GuiGraphicsExtractor g, Row r, int x, int y, int w, int mouseX, int mouseY) {
        int h = ROW_H - 2;
        boolean done = progress.isChecked(r.id());
        boolean legacy = FarmListClient.BUILDING_BLOCK.equals(r.id());
        Families.Family fam = Families.byKey(r.id());
        boolean inList = Ui.in(mouseX, mouseY, listX, listY, listW, listH);
        boolean hover = inList && Ui.in(mouseX, mouseY, x, y, w, h);

        int face = done ? (hover ? 0xFF3F5F3F : 0xFF324A32) : (hover ? 0xFF4A4A4A : 0xFF373737);
        Ui.inset(g, x, y, w, h, face);
        Ui.checkbox(g, x + 5, y + (h - 12) / 2, done);
        Ui.slot(g, x + 22, y + (h - 18) / 2);

        // what to show: the item itself, or for an old building-block row, the block picked so far
        String choice = legacy ? progress.getChoice(r.id()) : null;
        String shownId = legacy ? choice : r.id();
        FarmListClient.CatalogEntry entry = shownId == null ? null : FarmListClient.entryFor(shownId);
        boolean placeholder = entry == null && (legacy || fam != null);
        net.minecraft.world.item.ItemStack famIcon = (entry == null && fam != null) ? Families.iconFor(fam) : null;
        if (entry != null) {
            Ui.icon(g, entry.stack(), x + 23, y + (h - 16) / 2);
        } else if (famIcon != null) {
            Ui.icon(g, famIcon, x + 23, y + (h - 16) / 2);
        } else {
            Ui.textCenter(g, this.font, "?", x + 31, y + (h - 9) / 2, Ui.WHITE, true);
        }

        // pencil on the right edge
        int pencilX = x + w - PENCIL - 5;
        Ui.pencilButton(g, pencilX, y + (h - PENCIL) / 2, inList && Ui.in(mouseX, mouseY, pencilX, y + (h - PENCIL) / 2, PENCIL, PENCIL));
        int rightEdge = pencilX - 6;

        // "x268" until you have a count, then "56/268" (what you have / what you need)
        Integer have = progress.getHave(r.id());
        String countText = have == null ? "x" + r.count() : have + "/" + r.count();
        int countColor;
        if (have == null) {
            countColor = done ? 0xFF9FD89F : Ui.WHITE;
        } else if (have >= r.count()) {
            countColor = 0xFF9FD89F;
        } else if (have > 0) {
            countColor = Ui.GOLD;
        } else {
            countColor = 0xFFAAAAAA;
        }
        int countW = this.font.width(countText);
        Ui.textRight(g, this.font, countText, rightEdge, y + (h - 9) / 2, countColor, true);

        // name, kept clear of the count
        int nameX = x + 46;
        int nameMax = rightEdge - countW - 8 - nameX;
        int color = done ? 0xFF9FD89F : Ui.WHITE;
        if (placeholder) {
            String head = legacy ? "Building block" : fam.label();
            String sub = legacy ? (entry != null ? entry.name() : "pick a block with the pencil")
                    : "pick a type with the pencil";
            Ui.text(g, this.font, Ui.fit(this.font, head, nameMax), nameX, y + 3, color, true);
            Ui.text(g, this.font, Ui.fit(this.font, sub, nameMax), nameX, y + 13, done ? 0xFF9FD89F : 0xFFAAAAAA, true);
        } else {
            String name = Ui.fit(this.font, entry != null ? entry.name() : r.id(), nameMax);
            int ty = y + (h - 9) / 2;
            Ui.text(g, this.font, name, nameX, ty, color, true);
            if (done) {
                g.fill(nameX, ty + 4, nameX + this.font.width(name), ty + 5, color);   // strike-through
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
