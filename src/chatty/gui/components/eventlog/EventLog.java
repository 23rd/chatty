
package chatty.gui.components.eventlog;

import chatty.gui.MainGui;
import chatty.lang.Language;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 *
 * @author tduva
 */
public class EventLog extends JDialog {
    
    private final MainGui g;
    private final EventList notificationList;
    private final EventList systemList;
    private final JTabbedPane tabs;
    private final JButton systemMarkRead;
    
    public EventLog(MainGui g) {
        super(g);
        setTitle(Language.getString("eventLog.title"));
        this.g = g;
        
        notificationList = new EventList(this);
        systemList = new EventList(this);
        
        setLayout(new BorderLayout());
        
        JScrollPane scroll1 = new JScrollPane(notificationList);
        scroll1.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        
        JScrollPane scroll2 = new JScrollPane(systemList);
        scroll2.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        
        tabs = new JTabbedPane();
        // Disabled for now
//        tabs.addTab("Notifications", scroll1);
        JPanel systemPanel = new JPanel();
        systemPanel.setLayout(new BorderLayout());
        systemMarkRead = new JButton("Mark all as read");
        systemPanel.add(systemMarkRead, BorderLayout.SOUTH);
        systemPanel.add(scroll2, BorderLayout.CENTER);
        tabs.addTab("Chatty Info", systemPanel);
        
        systemMarkRead.addActionListener(e -> {
            if (g.getSettings().getList("readEvents").isEmpty()) {
                JOptionPane.showMessageDialog(this, Language.getString("eventLog.firstMarkReadNote"));
            }
            for (String id : systemList.getEventIds()) {
                g.getSettings().setAdd("readEvents", id);
            }
            updateEventState();
        });
        
        add(tabs, BorderLayout.CENTER);
        
        for (int i=0;i<100;i++) {
            systemList.addEvent(new Event(Event.Type.SYSTEM, null, "Test "+i, "Note: You can also open the Event Log through the 'View'-menu. "+i, null, null));
        }
        
        setSize(300, 400);
    }
    
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            updateEventState();
            SwingUtilities.invokeLater(() -> {
                systemList.fixLayout();
                notificationList.fixLayout();
            });
        }
    }
    
    public void setTab(int tab) {
        // Disabled for now
//        tabs.setSelectedIndex(tab);
    }
    
    protected boolean isReadEvent(String id) {
        return id != null && g.getSettings().listContains("readEvents", id);
    }
    
    private void updateEventState() {
        systemList.repaint();
        int count = getNewSystemEvents();
        g.setSystemEventCount(count);
        systemMarkRead.setEnabled(count > 0);
    }
    
    public int getNewSystemEvents() {
        return systemList.getNewEvents();
    }
    
    public void add(Event event) {
        if (event.type == Event.Type.NOTIFICATION) {
            // Disabled for now
//            notificationList.addEvent(event);
        }
        else {
            systemList.addEvent(event);
            updateEventState();
        }
    }
    
    private static EventLog main;
    
    public static void addSystemEvent(String id, String title, String text) {
        if (main != null) {
            main.add(new Event(Event.Type.SYSTEM, id, title, text, null, null));
        }
    }
    
    public static void setMain(EventLog main) {
        EventLog.main = main;
    }
    
}
