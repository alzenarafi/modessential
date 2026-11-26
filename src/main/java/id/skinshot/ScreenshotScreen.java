package id.skinshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Layar daftar screenshot: buka, hapus (2 klik), dan buka folder. */
public class ScreenshotScreen extends Screen {

	private static final int PER_PAGE = 6;
	private static final int ROW_HEIGHT = 24;

	private final Screen parent;
	private final Path dir;

	private List<Path> files = List.of();
	private int page = 0;
	private Path pendingDelete = null;

	public ScreenshotScreen(Screen parent) {
		super(Component.literal("Screenshot Manager"));
		this.parent = parent;
		this.dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
	}

	private void reload() {
		try (Stream<Path> stream = Files.isDirectory(dir) ? Files.list(dir) : Stream.<Path>empty()) {
			files = stream
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
					.sorted(Comparator.comparingLong(ScreenshotScreen::modified).reversed())
					.toList();
		} catch (IOException e) {
			files = List.of();
		}
		int lastPage = Math.max(0, (files.size() - 1) / PER_PAGE);
		page = Math.max(0, Math.min(page, lastPage));
	}

	private static long modified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}

	private static String label(Path p) {
		long kb = 0;
		try {
			kb = Files.size(p) / 1024;
		} catch (IOException ignored) {
		}
		String name = p.getFileName().toString();
		if (name.length() > 30) name = name.substring(0, 27) + "...";
		return name + "  (" + kb + " KB)";
	}

	@Override
	protected void init() {
		reload();
		int cx = this.width / 2;
		int top = 40;

		int from = page * PER_PAGE;
		int to = Math.min(files.size(), from + PER_PAGE);
		for (int i = from; i < to; i++) {
			Path p = files.get(i);
			int y = top + (i - from) * ROW_HEIGHT;

			this.addRenderableWidget(Button.builder(Component.literal(label(p)), b -> open(p))
					.bounds(cx - 150, y, 235, 20).build());

			boolean confirming = p.equals(pendingDelete);
			this.addRenderableWidget(Button.builder(Component.literal(confirming ? "Yakin?" : "Hapus"), b -> delete(p))
					.bounds(cx + 90, y, 60, 20).build());
		}

		int bottom = this.height - 28;

		Button prev = Button.builder(Component.literal("<"), b -> {
			page--;
			pendingDelete = null;
			this.rebuildWidgets();
		}).bounds(cx - 150, bottom, 30, 20).build();
		prev.active = page > 0;
		this.addRenderableWidget(prev);

		Button next = Button.builder(Component.literal(">"), b -> {
			page++;
			pendingDelete = null;
			this.rebuildWidgets();
		}).bounds(cx - 115, bottom, 30, 20).build();
		next.active = to < files.size();
		this.addRenderableWidget(next);

		this.addRenderableWidget(Button.builder(Component.literal("Buka Folder"), b -> openFolder())
				.bounds(cx - 75, bottom, 110, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Tutup"), b -> this.onClose())
				.bounds(cx + 40, bottom, 110, 20).build());
	}

	/** Buka file/folder pake aplikasi bawaan OS (gak bergantung ke class Minecraft). */
	private static void openPath(Path p) {
		String target = p.toAbsolutePath().toString();
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		try {
			if (os.contains("win")) {
				new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target).start();
			} else if (os.contains("mac")) {
				new ProcessBuilder("open", target).start();
			} else {
				new ProcessBuilder("xdg-open", target).start();
			}
		} catch (IOException ignored) {
		}
	}

	private void open(Path p) {
		openPath(p);
	}

	private void openFolder() {
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
		}
		openPath(dir);
	}

	private void delete(Path p) {
		if (!p.equals(pendingDelete)) {
			// klik pertama = minta konfirmasi
			pendingDelete = p;
		} else {
			try {
				Files.deleteIfExists(p);
			} catch (IOException ignored) {
			}
			pendingDelete = null;
		}
		this.rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		String title = "Screenshot Manager";
		graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 15, 0xFFFFFFFF, true);

		if (files.isEmpty()) {
			String empty = "Belum ada screenshot (tekan F2 di dalam game)";
			graphics.text(this.font, empty, (this.width - this.font.width(empty)) / 2, 60, 0xFFAAAAAA, true);
		} else {
			int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
			String info = "Halaman " + (page + 1) + "/" + pages + "  -  " + files.size() + " file";
			graphics.text(this.font, info, (this.width - this.font.width(info)) / 2, this.height - 42, 0xFFAAAAAA, true);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
