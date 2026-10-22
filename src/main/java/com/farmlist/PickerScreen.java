package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The checklist picker. Each checklist has a color square (gray = not started, yellow = in progress,
 * green = complete), its checked/total count, and a few item icons from inside it.
 */
public class PickerScreen extends Screen {

    private static final int ROW_H = 26;
    private static final int HEADER_H = 50;
    private static final int FOOTER_H = 34;

    static final int COLOR_NOT_STARTED = 0xFF888888;
    static final int COLOR_IN_PROGRESS = 0xFFFFD755;
    static final int COLOR_COMPLETE = 0xFF55FF55;

    private record Entry(String file, String name, int checked, int total, int color, List<ItemStack> icons) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private int scroll = 0;
    private boolean dragging = false;

    private int px, py, pw, ph;
    private int listX, listY, listW, listH;

    public PickerScreen() {
        super(Component.literal("Farm Checklist"));
    }

    @Override
    protected void init() {
        pw = Math.min(this.width - 16, 320);
        ph = Math.min(this.height - 16, 340);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        listX = px + 8;
        listW = pw - 16 - Ui.SB_W - 2;
        listY = py + HEADER_H;
        listH = ph - HEADER_H - FOOTER_H;

        loadEntries();
        clampScroll();

        int bw = (pw - 16 - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("New Checklist"), b ->
                this.minecraft.gui.setScreen(new NameScreen())
        ).bounds(px + 8, py + 20, bw, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Import from Clipboard"), b -> {
            String imported = FarmListClient.importFromClipboard();
            if (imported != null) {
                this.minecraft.gui.setScreen(FarmListClient.pendingReview.isEmpty()
                        ? new ChecklistScreen(imported) : new ReviewScreen(imported));
            }
        }).bounds(px + 8 + bw + 4, py + 20, bw, 20).build());
    }

    private void loadEntries() {
        entries.clear();
        for (String file : FarmListClient.listTranscriptions()) {
            String name = FarmListClient.baseName(file);
            Progress progress = new Progress(name);
            Map<String, Integer> required = FarmListClient.parseRequired(
                    FarmListClient.TRANSCRIPTION_DIR.resolve(file));

            int checked = 0;
            List<ItemStack> icons = new ArrayList<>();
            for (String id : required.keySet()) {
                if (progress.isChecked(id)) checked++;
                if (icons.size() >= 4) continue;
                if (FarmListClient.isPlaceholder(id) && FarmListClient.entryFor(FarmListClient.countedId(id, progress)) == null) continue;
                FarmListClient.CatalogEntry entry = FarmListClient.entryFor(FarmListClient.countedId(id, progress));
                if (entry != null) icons.add(entry.stack());
            }

            int color;
            if (progress.isComplete()) {
                color = COLOR_COMPLETE;
            } else if (checked > 0) {
                color = COLOR_IN_PROGRESS;
            } else {
                color = COLOR_NOT_STARTED;
            }
            entries.add(new Entry(file, name, checked, required.size(), color, icons));
        }
    }

    // ---------- scrolling and clicking ----------

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_H - listH);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (event.button() == 0 && Ui.in(mx, my, listX + listW + 2, listY, Ui.SB_W, listH)) {
            dragging = true;
            scroll = Ui.scrollFromY(my, listY, listH, entries.size() * ROW_H, listH);
            return true;
        }
        if (event.button() == 0 && Ui.in(mx, my, listX, listY, listW, listH)) {
            int index = (my - listY + scroll) / ROW_H;
            if (index >= 0 && index < entries.size()
                    && (my - listY + scroll) % ROW_H < ROW_H - 2) {
                this.minecraft.gui.setScreen(new ChecklistScreen(entries.get(index).file()));
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            scroll = Ui.scrollFromY((int) event.y(), listY, listH, entries.size() * ROW_H, listH);
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
        super.extractRenderState(g, mouseX, mouseY, partialTick);   // the two buttons

        Ui.textCenter(g, this.font, "Your Checklists", px + pw / 2, py + 7, Ui.TEXT_DARK, false);

        if (entries.isEmpty()) {
            Ui.textCenter(g, this.font, "No checklists yet.", px + pw / 2, listY + 12, Ui.TEXT_DARK, false);
            Ui.textCenter(g, this.font, "Make a new one or import from the clipboard.", px + pw / 2, listY + 24, Ui.TEXT_MUTED, false);
        }

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < entries.size(); i++) {
            int y = listY + i * ROW_H - scroll;
            if (y + ROW_H < listY || y > listY + listH) continue;
            drawEntry(g, entries.get(i), listX, y, listW, mouseX, mouseY);
        }
        g.disableScissor();
        Ui.scrollbar(g, listX + listW + 2, listY, listH, scroll, entries.size() * ROW_H, listH);

        // status line and color legend
        if (!FarmListClient.status.isEmpty()) {
            Ui.text(g, this.font, Ui.fit(this.font, FarmListClient.status, pw - 16),
                    px + 8, py + ph - 28, Ui.TEXT_DARK, false);
        }
        int lx = px + 8;
        int ly = py + ph - 15;
        lx = legend(g, lx, ly, COLOR_NOT_STARTED, "Not started");
        lx = legend(g, lx + 8, ly, COLOR_IN_PROGRESS, "In progress");
        legend(g, lx + 8, ly, COLOR_COMPLETE, "Complete");
    }

    /** Draws one legend item and returns the x where it ended. */
    private int legend(GuiGraphicsExtractor g, int x, int y, int color, String label) {
        g.fill(x, y, x + 8, y + 8, 0xFF000000);
        g.fill(x + 1, y + 1, x + 7, y + 7, color);
        Ui.text(g, this.font, label, x + 11, y, Ui.TEXT_DARK, false);
        return x + 11 + this.font.width(label);
    }

    private void drawEntry(GuiGraphicsExtractor g, Entry e, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = Ui.in(mouseX, mouseY, listX, listY, listW, listH)
                && Ui.in(mouseX, mouseY, x, y, w, ROW_H - 2);
        Ui.inset(g, x, y, w, ROW_H - 2, hover ? 0xFF4A4A4A : 0xFF373737);
        Ui.statusSquare(g, x + 6, y + 6, e.color());

        String countText = e.checked() + "/" + e.total();
        int countX = x + w - 6 - this.font.width(countText);
        Ui.text(g, this.font, countText, countX, y + 8, Ui.WHITE, true);

        int iconsX = countX - 8 - e.icons().size() * 18;
        for (int i = 0; i < e.icons().size(); i++) {
            Ui.icon(g, e.icons().get(i), iconsX + i * 18, y + 4);
        }

        int nameX = x + 24;
        int nameMax = iconsX - nameX - 6;
        Ui.text(g, this.font, Ui.fit(this.font, e.name(), nameMax), nameX, y + 8, Ui.WHITE, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
