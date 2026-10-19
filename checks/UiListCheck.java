import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;

/** The children of an element were walked while they were live: a handler
    that removed a sibling made the same widget be visited twice, and a widget
    that removed a sibling while it was drawn threw and took the client with
    it. */
public class UiListCheck
{
    static int passed = 0;
    static int failed = 0;

    static void check(String label, boolean ok)
    {
        if (ok)
        {
            passed++;
        }
        else
        {
            failed++;
            System.out.println("  FAIL " + label);
        }
    }

    /** A widget that removes a sibling but does not take the click. */
    static class QuietRemover extends UIElement
    {
        UIElement victim;
        int hits;

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            this.hits += 1;
            this.victim.removeFromParent();

            return false;
        }
    }

    /** A widget that removes a sibling while it is drawn. */
    static class RemoverOnRender extends UIElement
    {
        UIElement victim;

        @Override
        public void render(UIContext context)
        {
            this.victim.removeFromParent();
        }
    }

    public static void main(String[] args)
    {
        UIContext context = new UIContext(null);

        context.pushViewport(new Area(0, 0, 400, 300));

        System.out.println("== un clic no se entrega dos veces ==");

        UIElement parent = new UIElement();
        QuietRemover remover = new QuietRemover();
        UIElement victim = new UIElement();

        remover.victim = victim;
        parent.add(victim);
        parent.add(remover);

        context.setMouse(10, 10, 0);
        parent.mouseClicked(context);

        check("el widget recibe un solo clic", remover.hits == 1);
        check("y el hermano se quito", parent.getChildren().size() == 1);

        System.out.println("== dibujar un widget que quita a otro no rompe nada ==");

        UIElement drawing = new UIElement();
        UIElement second = new UIElement();
        RemoverOnRender first = new RemoverOnRender();

        first.victim = second;
        first.noCulling();
        second.noCulling();
        drawing.add(second);
        drawing.add(first);

        boolean thrown = false;

        try
        {
            drawing.render(context);
        }
        catch (RuntimeException e)
        {
            thrown = true;

            System.out.println("       " + e.getClass().getName() + ": " + e.getMessage());
        }

        check("no lanza nada", !thrown);
        check("y el hermano se quito", drawing.getChildren().size() == 1);

        System.out.println("== quitar un hijo lo despega de verdad ==");

        UIElement holder = new UIElement();
        UIElement child = new UIElement();

        holder.add(child);
        holder.remove((IUIElement) child);

        check("el hijo ya no esta en la lista", holder.getChildren().isEmpty());
        check("y no tiene padre", child.getParent() == null);
        check("y no se puede ver", !child.canBeSeen());

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }
}
