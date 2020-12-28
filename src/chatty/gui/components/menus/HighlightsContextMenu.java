
package chatty.gui.components.menus;

import chatty.lang.Language;
import java.awt.event.ActionEvent;

/**
 * Context menu for the Highlights/Ignored Messages dialog (also used for Stream
 * Chat dialog currently).
 * 
 * @author tduva
 */
public class HighlightsContextMenu extends ContextMenu {

    public HighlightsContextMenu(boolean isDocked, boolean autoOpen) {
        addItem("clearHighlights", Language.getString("highlightedDialog.cm.clear"));
        addSeparator();
        addCheckboxItem("toggleDock", "Dock", isDocked);
        addCheckboxItem("toggleAutoOpen", "Open on message", autoOpen);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        for (ContextMenuListener l : getContextMenuListeners()) {
            l.menuItemClicked(e);
        }
    }
    
}
