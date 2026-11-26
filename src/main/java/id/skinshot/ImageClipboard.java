package id.skinshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Salin file PNG ke clipboard lewat alat bawaan OS (AWT tidak bisa diandalkan di Minecraft). */
final class ImageClipboard {
    private ImageClipboard() {}

    static CompletableFuture<Boolean> copy(Path file) {
        String path = file.toAbsolutePath().toString();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return CompletableFuture.supplyAsync(() -> {
            if (os.contains("win")) {
                // Path dikirim lewat environment variable, bukan disisipkan ke script.
                String script = "$ErrorActionPreference='Stop';"
                        + "Add-Type -AssemblyName System.Windows.Forms,System.Drawing;"
                        + "$i=[System.Drawing.Image]::FromFile($env:SKINSHOT_FILE);"
                        + "try{[System.Windows.Forms.Clipboard]::SetImage($i)}finally{$i.Dispose()}";
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile",
                        "-NonInteractive", "-STA", "-Command", script);
                pb.environment().put("SKINSHOT_FILE", path);
                return run(pb);
            }
            if (os.contains("mac")) {
                return run(new ProcessBuilder("osascript",
                        "-e", "on run argv",
                        "-e", "set the clipboard to (read (POSIX file (item 1 of argv)) as «class PNGf»)",
                        "-e", "end run", path));
            }
            for (List<String> cmd : List.of(
                    List.of("wl-copy", "--type", "image/png"),
                    List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-i"))) {
                ProcessBuilder pb = new ProcessBuilder(cmd).redirectInput(file.toFile());
                if (run(pb)) return true;
            }
            return false;
        });
    }

    private static boolean run(ProcessBuilder pb) {
        try {
            Process p = pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
