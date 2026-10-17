package mchorse.bbs_mod.ui.dashboard.panels.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
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
                String backup = selected.get(0);
                UIContext context = this.getContext();

                this.repository.restoreBackup(this.id, backup, (restored) ->
                {
                    if (restored)
                    {
                        context.notifySuccess(UIKeys.PANELS_BACKUPS_RESTORED_NOTIFICATION.format(backup));

                        if (this.onRestored != null)
                        {
                            this.onRestored.accept(true);
                        }
                    }
                    else
                    {
                        context.notifyError(UIKeys.PANELS_BACKUPS_RESTORE_ERROR.format(backup));
                    }
                });

                this.close();
            }
        });

        this.repository.requestBackups(this.id, this::addValues);
    }
}