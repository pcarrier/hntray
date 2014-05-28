import com.eclipsesource.json.JsonObject;
import lombok.Data;
import lombok.SneakyThrows;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static javax.swing.JOptionPane.ERROR_MESSAGE;

public final class HnTray {
    private static final Toolkit TK = Toolkit.getDefaultToolkit();
    private static final Logger LOG = Logger.getAnonymousLogger();
    private final TrayIcon icon = new TrayIcon(TK.createImage(HnTray.class.getResource("favicon.gif")));
    private final ScheduledThreadPoolExecutor sched = new ScheduledThreadPoolExecutor(1);

    public HnTray() throws AWTException {
        icon.setImageAutoSize(true);
        refreshMenu();
        SystemTray.getSystemTray().add(icon);
        sched.scheduleAtFixedRate(() -> refreshMenu(), 5, 5, TimeUnit.MINUTES);
    }

    public static void main(String[] args) throws AWTException {
        if (!SystemTray.isSupported()) {
            JOptionPane.showMessageDialog(null, "Your desktop sucks.", "Giving up!", ERROR_MESSAGE);
            System.exit(1);
        }
        new HnTray();
    }

    @SneakyThrows
    private static URI makeURI(String s) {
        return new URI(s);
    }

    private void refreshMenu() {
        try {
            icon.setPopupMenu(menuDuJour());
            LOG.info("Refreshed!");
        } catch (IOException e) {
            LOG.warning("Failed, retrying…");
            sched.schedule(() -> refreshMenu(), 1, TimeUnit.MINUTES);
        }
    }

    private PopupMenu menuDuJour() throws IOException {
        final PopupMenu menu = new PopupMenu();
        try (final Reader r = new InputStreamReader(new URL("http://api.ihackernews.com/page").openStream())) {
            JsonObject.readFrom(r).get("items").asArray().values().stream().map(v -> {
                final JsonObject o = v.asObject();
                return new Article(
                        o.get("id").asInt(),
                        o.get("title").asString(),
                        o.get("points").asInt(),
                        o.get("commentCount").asInt());
            }).sorted().forEach(a -> {
                final MenuItem item = new MenuItem(a.toString());
                item.addActionListener(e -> {
                    final String url = "https://news.ycombinator.com/item?id=" + a.getId();
                    try {
                        Desktop.getDesktop().browse(makeURI(url));
                    } catch (IOException e1) {
                        TK.getSystemClipboard().setContents(new StringSelection(url), null);
                    }
                });
                menu.add(item);
            });
        }
        menu.addSeparator();
        final MenuItem refresh = new MenuItem("Refresh");
        refresh.addActionListener((e) -> sched.execute(() -> refreshMenu()));
        menu.add(refresh);
        final MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(e -> System.exit(0));
        menu.add(quitItem);
        return menu;
    }

    @Data
    private static class Article implements Comparable<Article> {
        private final int id;
        private final String title;
        private final int points;
        private final int commentCount;

        @Override
        public int compareTo(Article o) {
            return o.getPoints() - getPoints();
        }

        @Override
        public String toString() {
            return String.format("%04d %s (%d)", getPoints(), getTitle(), getCommentCount());
        }
    }
}
