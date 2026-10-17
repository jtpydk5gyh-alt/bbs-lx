package mchorse.bbs_mod.ui.dashboard.panels.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.utils.repos.IRepository;

import java.util.function.Consumer;

public class UIBackupsOverlayPanel extends UIListOverlayPanel
{
    private final IRepository<?> repository;
    private final String id;
    private final Consumer<Boolean> onRestored;

    public UIBackupsOverlayPanel(IKey title, IRepository<?> repository, String id, Consumer<Boolean> onRestored)
    {
        super(title, null);

        this.repository = repository;
        this.id = id;
        this.onRestored = onRestored;

        this.callback((selected) ->
        {
            if (!selected.isEmpty())
            {
                this.repository.restoreBackup(this.id, selected.get(0), this.onRestored);
            }
        });

        this.repository.requestBackups(this.id, this::addValues);
    }
}