package id.skinshot;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class ScreenshotScreen extends Screen {
    private static final int BG = 0xF0181818, PANEL = 0xFF202020, HEADER = 0xFF262626;
    private static final int CARD = 0xFF2C2C2C, ACCENT = 0xFF2F81F7, TEXT = 0xFFFFFFFF, MUTED = 0xFFAAAAAA;
    private static final int GAP = 6, HEAD = 28, FOOT = 28, THUMB_MAX = 320;

    private record Thumb(Identifier id, int w, int h) {}

    private final Screen parent;
    private final Path dir;
    private final Path favFile;

    private List<Path> files = List.of();
    private final Set<String> favorites = new HashSet<>();
    private boolean onlyFav;
    private int page;
    private Path selected;          // null = tampilan grid
    private Path pendingDelete;
    private String status = "";

    private final Map<String, Thumb> textures = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private ExecutorService loader;
    private int generation;         // membuang hasil load yang sudah basi
    private int textureCounter;

    private int px, py, pw, ph, cols, rows, cellW, cellH;

    public ScreenshotScreen(Screen parent) {
        super(Component.literal("Galeri Screenshot"));
        this.parent = parent;
        this.dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        this.favFile = FabricLoader.getInstance().getConfigDir().resolve("skinshot-favorites.txt");
        loadFavorites();
    }

    // ---------- data ----------

    private void reload() {
        try (Stream<Path> s = Files.isDirectory(dir) ? Files.list(dir) : Stream.<Path>empty()) {
            files = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .filter(p -> !onlyFav || favorites.contains(name(p)))
                    .sorted((a, b) -> Long.compare(modified(b), modified(a)))
                    .toList();
        } catch (IOException e) {
            files = List.of();
            status = "Gagal membaca folder screenshot";
        }
        if (selected != null && !files.contains(selected)) selected = null;
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String name(Path p) {
        return p.getFileName().toString();
    }

    private void loadFavorites() {
        try {
            if (Files.exists(favFile)) favorites.addAll(Files.readAllLines(favFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            status = "Gagal membaca favorit";
        }
    }

    private void saveFavorites() {
        try {
            Files.createDirectories(favFile.getParent());
            Files.write(favFile, favorites, StandardCharsets.UTF_8);
        } catch (IOException e) {
            status = "Gagal menyimpan favorit";
        }
    }

    // ---------- tekstur ----------

    /** Ambil tekstur dari cache; kalau belum ada, load di thread terpisah. */
    private Thumb texture(Path file, boolean full) {
        String key = (full ? "full:" : "thumb:") + file;
        Thumb t = textures.get(key);
        if (t != null || !requested.add(key)) return t;
        int gen = generation;
        loader.execute(() -> {
            if (gen != generation) return;
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage img = NativeImage.read(in);
                if (!full && img.getWidth() > THUMB_MAX) {
                    int h = Math.max(1, img.getHeight() * THUMB_MAX / img.getWidth());
                    NativeImage small = new NativeImage(THUMB_MAX, h, false);
                    img.resizeSubRectTo(0, 0, img.getWidth(), img.getHeight(), small);
                    img.close();
                    img = small;
                }
                NativeImage result = img;
                minecraft.execute(() -> {
                    if (gen != generation) {
                        result.close();
                        return;
                    }
                    Identifier id = Identifier.fromNamespaceAndPath(SkinShotClient.MOD_ID, "shot/" + textureCounter++);
                    minecraft.getTextureManager().register(id, new DynamicTexture(id::toString, result));
                    textures.put(key, new Thumb(id, result.getWidth(), result.getHeight()));
                });
            } catch (Exception e) {
                // File rusak atau masih ditulis: kartunya tetap kosong.
            }
        });
        return null;
    }

    private void releaseTextures() {
        generation++;
        textures.values().forEach(t -> minecraft.getTextureManager().release(t.id()));
        textures.clear();
        requested.clear();
    }

    // ---------- layout ----------

    @Override
    protected void init() {
        if (loader == null) {
            loader = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "skinshot-loader");
                t.setDaemon(true);
                return t;
            });
        }
        reload();

        pw = Math.min(width - 16, 520);
        ph = height - 16;
        px = (width - pw) / 2;
        py = 8;
        cols = pw >= 420 ? 4 : 3;
        cellW = (pw - GAP * (cols + 1)) / cols;
        cellH = cellW * 9 / 16;
        rows = Math.max(1, (ph - HEAD - FOOT - GAP) / (cellH + GAP));
        page = Math.max(0, Math.min(page, pages() - 1));

        if (selected == null) initGrid();
        else initViewer();
    }

    private int pages() {
        return Math.max(1, (files.size() + cols * rows - 1) / (cols * rows));
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, 20).build());
    }

    private void initGrid() {
        button(onlyFav ? "Semua" : "[Semua]", px + 4, py + 4, 56, () -> setFilter(false));
        button(onlyFav ? "[Favorit]" : "Favorit", px + 64, py + 4, 56, () -> setFilter(true));
        button("Folder", px + pw - 108, py + 4, 50, this::openFolder);
        button("Tutup", px + pw - 54, py + 4, 50, this::onClose);

        int y = py + ph - 24;
        button("<", px + 4, y, 20, () -> turnPage(-1)).active = page > 0;
        button(">", px + 28, y, 20, () -> turnPage(1)).active = page < pages() - 1;
    }

    private void initViewer() {
        button("Kembali", px + 4, py + 4, 60, this::back);
        int w = Math.min(70, (pw - 4 * GAP) / 5);
        int x = px + (pw - (w * 5 + GAP * 3)) / 2;
        int y = py + ph - 24;
        Path file = selected;
        button("Copy", x, y, w, () -> copy(file));
        button(favorites.contains(name(file)) ? "Unfavorit" : "Favorit", x + (w + GAP), y, w, () -> toggleFavorite(file));
        button("Buka", x + 2 * (w + GAP), y, w, () -> openPath(file));
        button(file.equals(pendingDelete) ? "Yakin?" : "Hapus", x + 3 * (w + GAP), y, w, () -> delete(file));
        int i = files.indexOf(file);
        button("<", px + pw - 48, py + 4, 20, () -> step(-1)).active = i > 0;
        button(">", px + pw - 24, py + 4, 20, () -> step(1)).active = i < files.size() - 1;
    }

    // ---------- aksi ----------

    private void setFilter(boolean fav) {
        onlyFav = fav;
        page = 0;
        status = "";
        releaseTextures();
        rebuildWidgets();
    }

    private void turnPage(int delta) {
        int next = Math.max(0, Math.min(page + delta, pages() - 1));
        if (next == page) return;
        page = next;
        releaseTextures();   // hanya simpan tekstur halaman yang terlihat
        rebuildWidgets();
    }

    private void select(Path file) {
        selected = file;
        pendingDelete = null;
        status = "";
        rebuildWidgets();
    }

    private void step(int delta) {
        int i = files.indexOf(selected) + delta;
        if (i < 0 || i >= files.size()) return;
        releaseTextures();
        select(files.get(i));
    }

    private void back() {
        releaseTextures();
        select(null);
    }

    private void copy(Path file) {
        status = "Menyalin...";
        ImageClipboard.copy(file).thenAccept(ok -> minecraft.execute(() ->
                status = ok ? "Tersalin ke clipboard!" : "Gagal menyalin ke clipboard"));
    }

    private void toggleFavorite(Path file) {
        if (!favorites.remove(name(file))) favorites.add(name(file));
        saveFavorites();
        rebuildWidgets();
    }

    private void delete(Path file) {
        if (!file.equals(pendingDelete)) {
            pendingDelete = file;
        } else {
            try {
                Files.deleteIfExists(file);
                if (favorites.remove(name(file))) saveFavorites();
                releaseTextures();
                selected = null;
                status = "Screenshot dihapus";
            } catch (IOException e) {
                status = "Gagal menghapus";
            }
            pendingDelete = null;
        }
        rebuildWidgets();
    }

    private void openFolder() {
        try {
            Files.createDirectories(dir);
            openPath(dir);
        } catch (IOException e) {
            status = "Gagal membuka folder";
        }
    }

    private void openPath(Path path) {
        String p = path.toAbsolutePath().toString();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", p).start();
            else new ProcessBuilder(os.contains("mac") ? "open" : "xdg-open", p).start();
        } catch (IOException e) {
            status = "Gagal membuka";
        }
    }

    // ---------- input ----------

    private int cardAt(double mx, double my) {
        int start = page * cols * rows;
        for (int n = 0; n < cols * rows && start + n < files.size(); n++) {
            int x = px + GAP + (n % cols) * (cellW + GAP);
            int y = py + HEAD + GAP + (n / cols) * (cellH + GAP);
            if (mx >= x && mx < x + cellW && my >= y && my < y + cellH) return start + n;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (selected == null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int i = cardAt(event.x(), event.y());
            if (i >= 0) {
                select(files.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy == 0) return false;
        if (selected == null) turnPage(sy > 0 ? -1 : 1);
        else step(sy > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selected != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) { back(); return true; }
            if (event.key() == InputConstants.KEY_LEFT) { step(-1); return true; }
            if (event.key() == InputConstants.KEY_RIGHT) { step(1); return true; }
            if (event.key() == InputConstants.KEY_C) { copy(selected); return true; }
        }
        return super.keyPressed(event);
    }

    // ---------- gambar ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.fill(px, py, px + pw, py + HEAD, HEADER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (selected == null) drawGrid(g, mouseX, mouseY);
        else drawViewer(g);
    }

    private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (files.isEmpty()) {
            String msg = onlyFav ? "Belum ada favorit" : "Belum ada screenshot (tekan F2 di dalam game)";
            centered(g, msg, py + ph / 2, MUTED);
        }
        int hover = cardAt(mouseX, mouseY);
        int start = page * cols * rows;
        for (int n = 0; n < cols * rows && start + n < files.size(); n++) {
            int x = px + GAP + (n % cols) * (cellW + GAP);
            int y = py + HEAD + GAP + (n / cols) * (cellH + GAP);
            Path file = files.get(start + n);
            if (start + n == hover) g.fill(x - 2, y - 2, x + cellW + 2, y + cellH + 2, ACCENT);
            g.fill(x, y, x + cellW, y + cellH, CARD);
            Thumb t = texture(file, false);
            if (t != null) drawFit(g, t, x, y, cellW, cellH);
            if (favorites.contains(name(file))) g.text(font, "★", x + cellW - 10, y + 3, 0xFFFFD23F, true);
        }
        String info = status.isEmpty()
                ? files.size() + " gambar - halaman " + (page + 1) + "/" + pages()
                : status;
        g.text(font, info, px + 56, py + ph - 18, MUTED, true);
    }

    private void drawViewer(GuiGraphicsExtractor g) {
        String title = font.plainSubstrByWidth(name(selected), pw - 130);
        centered(g, title, py + 10, TEXT);
        int x = px + GAP, y = py + HEAD + GAP;
        int w = pw - 2 * GAP, h = ph - HEAD - FOOT - 2 * GAP - 10;
        Thumb t = texture(selected, true);
        if (t != null) drawFit(g, t, x, y, w, h);
        else centered(g, "Memuat...", y + h / 2, MUTED);
        centered(g, status, y + h + 3, MUTED);
    }

    private void drawFit(GuiGraphicsExtractor g, Thumb t, int x, int y, int w, int h) {
        float scale = Math.min((float) w / t.w(), (float) h / t.h());
        int dw = Math.max(1, (int) (t.w() * scale));
        int dh = Math.max(1, (int) (t.h() * scale));
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x + (w - dw) / 2, y + (h - dh) / 2,
                0f, 0f, dw, dh, t.w(), t.h(), t.w(), t.h());
    }

    private void centered(GuiGraphicsExtractor g, String s, int y, int color) {
        g.text(font, s, px + (pw - font.width(s)) / 2, y, color, true);
    }

    @Override
    public void removed() {
        releaseTextures();
        if (loader != null) {
            loader.shutdownNow();
            loader = null;
        }
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
