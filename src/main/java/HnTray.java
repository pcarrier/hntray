import com.eclipsesource.json.JsonObject;
import lombok.Data;
import lombok.SneakyThrows;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static javax.swing.JOptionPane.*;

public final class HnTray {
    static final Toolkit TK = Toolkit.getDefaultToolkit();
    final Set<Integer> alreadySeen = Collections.newSetFromMap(new ConcurrentHashMap<>());
    final ScheduledThreadPoolExecutor sched = new ScheduledThreadPoolExecutor(1);
    final Logger log = Logger.getAnonymousLogger();
    final TrayIcon icon = new TrayIcon(TK.createImage(HnTray.class.getResource("favicon.gif")), "HnTray");
    volatile List<Article> articles = null;

    public HnTray() throws IOException, AWTException {
        icon.setImageAutoSize(true);
        icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                final PopupMenu menu = makeMenu();
                final Frame frame = new Frame();
                frame.add(menu);
                frame.setUndecorated(true);
                frame.setVisible(true);
                menu.show(frame, e.getXOnScreen(), e.getYOnScreen());
            }
        });

        SystemTray.getSystemTray().add(icon);
        sched.scheduleAtFixedRate(() -> {
            try {
                refreshArticles();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    public static void main(String[] args) {
        if (!SystemTray.isSupported()) {
            showMessageDialog(null, "Your desktop sucks.", "Giving up!", ERROR_MESSAGE);
            System.exit(1);
        }
        EventQueue.invokeLater(() -> {
            try {
                new HnTray();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @SneakyThrows
    private static URI makeURI(String s) {
        return new URI(s);
    }

    private void refreshArticles() throws IOException {
        log.fine("Refreshing...");
        try (final Reader r = new InputStreamReader(new URL("http://api.ihackernews.com/page").openStream())) {
            this.articles = JsonObject.readFrom(r).get("items").asArray().values()
                    .stream()
                    .map(v -> {
                        final JsonObject o = v.asObject();
                        return new Article(
                                o.get("id").asInt(),
                                o.get("title").asString(),
                                o.get("points").asInt(),
                                o.get("commentCount").asInt());
                    }).sorted().collect(Collectors.toList());
            log.info("Found " + articles.size() + " articles");
        }
        log.fine("Refreshed.");
    }

    private PopupMenu makeMenu() {
        final PopupMenu menu = new PopupMenu();
        if (articles.isEmpty())
            menu.add("Loading…");
        else {
            final List<Article> seenArticles = new ArrayList<>(),
                    newArticles = new ArrayList<>();
            articles.stream().forEach(a -> (alreadySeen.contains(a.getId()) ? seenArticles : newArticles).add(a));
            newArticles.forEach(a -> menu.add(a.toMenuItem()));
            if (!seenArticles.isEmpty())
                menu.addSeparator();
            seenArticles.forEach(a -> menu.add(a.toMenuItem()));
        }

        menu.addSeparator();

        final MenuItem refreshItem = new MenuItem("Refresh");
        refreshItem.addActionListener((evt) -> sched.execute(() -> {
            try {
                refreshArticles();
            } catch (IOException e) {
                showMessageDialog(null, e.getMessage(), "Refresh failed", WARNING_MESSAGE);
            }
        }));
        menu.add(refreshItem);

        final MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener((evt) -> System.exit(0));
        menu.add(quitItem);

        return menu;
    }

    @Data
    class Article implements Comparable<Article> {
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

        public MenuItem toMenuItem() {
            final MenuItem item = new MenuItem(toString());
            item.addActionListener(e -> {
                alreadySeen.add(getId());
                final String url = "https://news.ycombinator.com/item?id=" + getId();
                try {
                    Desktop.getDesktop().browse(makeURI(url));
                } catch (IOException e1) {
                    TK.getSystemClipboard().setContents(new StringSelection(url), null);
                }
            });
            return item;
        }
    }
}
