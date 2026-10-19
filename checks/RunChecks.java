import mchorse.bbs_mod.camera.values.ValueExpression;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.storage.DataFileStorage;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.network.PacketCrusher;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.manager.BaseManager;
import mchorse.bbs_mod.utils.manager.storage.CompressedDataStorage;
import mchorse.bbs_mod.utils.resources.FilteredLink;
import mchorse.bbs_mod.utils.resources.MultiLink;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Checks that run against the real classes, not against a reimplementation of
 * them. A check that reimplements the rule it is testing cannot fail when the
 * rule breaks, which is exactly how a regression slipped through before.
 *
 * Run with the runtime classpath of the mod. See checks/README or the CI job.
 */
public class RunChecks
{
    static int passed = 0;
    static int failed = 0;

    static void check(String label, boolean ok)
    {
        System.out.println((ok ? "  ok   " : "  FAIL ") + label);

        if (ok)
        {
            passed++;
        }
        else
        {
            failed++;
        }
    }

    static void section(String title)
    {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    public static void main(String[] args) throws Exception
    {
        storageIsAtomic();
        pathsStayInside();
        arrayLengthsAreBounded();
        factoryReportsTheTruth();
        backupsAreListableAndRestorable();
        crusherHasALimit();
        malformedDataDoesNotBreakTheLoad();

        System.out.println();
        System.out.println(failed == 0
            ? "RESULT: " + passed + "/" + (passed + failed) + " correctos"
            : "RESULT: " + failed + " fallo(s) de " + (passed + failed));

        System.exit(failed == 0 ? 0 : 1);
    }

    /* --- 1. a film save that fails leaves the previous film intact --- */

    static void storageIsAtomic() throws Exception
    {
        section("el guardado de peliculas es atomico");

        File dir = Files.createTempDirectory("check-storage").toFile();
        File film = new File(dir, "peli.dat");
        File temp = new File(film.getAbsolutePath() + ".tmp");
        CompressedDataStorage storage = new CompressedDataStorage();

        storage.save(film, map("v1"));

        check("la pelicula se escribe", film.exists() && film.length() > 0);
        check("no queda temporal", !temp.exists());
        check("se relee", storage.load(film).getString("marca").equals("v1"));

        byte[] v1 = Files.readAllBytes(film.toPath());

        storage.save(film, map("v2"));

        byte[] v2 = Files.readAllBytes(film.toPath());

        check("una segunda escritura cambia el contenido", !Arrays.equals(v1, v2));
        check("y sigue leyendose bien", storage.load(film).getString("marca").equals("v2"));
        check("y no deja temporal", !temp.exists());

        boolean threw = false;

        try
        {
            new Failing(film).write(map("v3"));
        }
        catch (Exception e)
        {
            threw = true;
        }

        check("una escritura que falla a media lanza", threw);
        check("el archivo NO se ha truncado", Files.readAllBytes(film.toPath()).length == v2.length);
        check("sigue siendo la v2", storage.load(film).getString("marca").equals("v2"));
        check("el temporal se ha limpiado", !temp.exists());

        storage.save(film, map("v4"));

        check("despues del fallo se puede seguir escribiendo", storage.load(film).getString("marca").equals("v4"));
    }

    static class Failing extends DataFileStorage
    {
        Failing(File file)
        {
            super(file);
        }

        @Override
        protected OutputStream openTarget(File file) throws IOException
        {
            OutputStream real = super.openTarget(file);

            return new OutputStream()
            {
                int written;

                @Override
                public void write(int b) throws IOException
                {
                    if (++this.written > 16)
                    {
                        throw new IOException("disco lleno");
                    }

                    real.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException
                {
                    for (int i = 0; i < len; i++)
                    {
                        this.write(b[off + i]);
                    }
                }

                @Override
                public void close() throws IOException
                {
                    real.close();
                }
            };
        }
    }

    /* --- 2. a path built from data cannot leave its folder --- */

    static void pathsStayInside() throws Exception
    {
        section("las rutas construidas con datos no salen de su carpeta");

        File dir = Files.createTempDirectory("check-paths").toFile();
        File inside = new File(dir, "dentro");
        File outside = new File(dir, "fuera.json");

        inside.mkdirs();
        Files.write(outside.toPath(), "secreto".getBytes());

        check("IOUtils.inside acepta lo que esta dentro", IOUtils.inside(inside, new File(inside, "a/b.json")));
        check("IOUtils.inside rechaza lo que sale", !IOUtils.inside(inside, new File(inside, "../fuera.json")));
        check("IOUtils.inside rechaza el salto absoluto", !IOUtils.inside(inside, outside));
        check("IOUtils.inside acepta la propia carpeta", IOUtils.inside(dir, dir));
        check("IOUtils.inside acepta un retroceder que vuelve dentro", IOUtils.inside(inside, new File(inside, "a/../b.json")));

        check("una ruta de gestor que sale se rechaza", managerFile(inside, "../fuera") == null);
        check("una ruta de gestor normal se acepta", managerFile(inside, "dentro") != null);
        check("una ruta de gestor anidada se acepta", managerFile(inside, "a/b") != null);
    }

    static File managerFile(File folder, String name)
    {
        return new TestManager(() -> folder).getFile(name);
    }

    static class TestManager extends BaseManager<mchorse.bbs_mod.settings.values.core.ValueGroup>
    {
        TestManager(java.util.function.Supplier<File> folder)
        {
            super(folder);

            this.backUps = true;
        }

        @Override
        protected mchorse.bbs_mod.settings.values.core.ValueGroup createData(String id, MapType mapType)
        {
            return new mchorse.bbs_mod.settings.values.core.ValueGroup(id);
        }
    }

    /* --- 3. a length read from a stream cannot allocate without limit --- */

    static void arrayLengthsAreBounded() throws Exception
    {
        section("las longitudes leidas del stream estan acotadas");

        check("acepta 0", acceptsLength(0));
        check("acepta 1", acceptsLength(1));
        check("acepta 65536", acceptsLength(65536));
        check("acepta el limite", acceptsLength(mchorse.bbs_mod.data.DataStorageContext.MAX_ELEMENTS));
        check("rechaza el limite mas uno", !acceptsLength(mchorse.bbs_mod.data.DataStorageContext.MAX_ELEMENTS + 1));
        check("rechaza un valor negativo", !acceptsLength(-1));
        check("rechaza el maximo de int", !acceptsLength(Integer.MAX_VALUE));
    }

    static boolean acceptsLength(int declared)
    {
        try
        {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);

            out.writeInt(declared);

            mchorse.bbs_mod.data.DataStorageContext.readLength(
                new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));

            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /* --- 4. the factory says what actually happened --- */

    static void factoryReportsTheTruth() throws Exception
    {
        section("la factoria dice la verdad");

        mchorse.bbs_mod.utils.factory.MapFactory<Object, Object> factory =
            new mchorse.bbs_mod.utils.factory.MapFactory<>();
        mchorse.bbs_mod.resources.Link type = mchorse.bbs_mod.resources.Link.bbs("cosa");

        factory.register(type, Thing.class);

        check("registra el tipo", factory.getKeys().contains(type));
        check("crea una instancia", factory.create(type) instanceof Thing);
        check("unregister lo quita de verdad", unregisterRemoves(factory, type));

        String message = null;

        try
        {
            factory.create(mchorse.bbs_mod.resources.Link.bbs("nada"));
        }
        catch (IllegalStateException e)
        {
            message = e.getMessage();
        }

        check("un tipo desconocido dice que no esta registrado",
            message != null && message.contains("not part of this factory"));
    }

    static boolean unregisterRemoves(mchorse.bbs_mod.utils.factory.MapFactory<Object, Object> factory,
                                      mchorse.bbs_mod.resources.Link type)
    {
        factory.unregister(type);

        return !factory.getKeys().contains(type) && factory.getTypeClass(type) == null;
    }

    public static class Thing
    {
        public Thing()
        {
        }
    }

    /* --- 5. backups can be listed and restored --- */

    static void backupsAreListableAndRestorable() throws Exception
    {
        section("los backups se listan y se restauran");

        /* Outside a running game this setting does not exist, and save() would
           swallow the resulting NullPointerException and quietly make no
           backup at all. */
        mchorse.bbs_mod.BBSSettings.editorMinutesBackup =
            new mchorse.bbs_mod.settings.values.numeric.ValueBoolean("minutes_backup", false);

        File dir = Files.createTempDirectory("check-backups").toFile();
        TestManager manager = new TestManager(() -> dir);

        check("sin backups al principio", manager.getBackups("peli").isEmpty());

        manager.save("peli", map("v1"));

        check("el primer guardado no deja backup", manager.getBackups("peli").isEmpty());

        Thread.sleep(1100);

        manager.save("peli", map("v2"));

        List<File> backups = manager.getBackups("peli");

        check("el segundo guardado deja uno", backups.size() == 1);
        check("y se restaura", manager.restoreBackup("peli", backups.get(0)));

        File fake = new File(dir, "inventado.json");

        Files.write(fake.toPath(), "{\"marca\":\"hackeado\"}".getBytes());

        check("un fichero que el gestor no reconoce se rechaza", !manager.restoreBackup("peli", fake));
        check("los ids con barras funcionan", manager.save("carpeta/hija", map("x")));

        Thread.sleep(1100);

        manager.save("carpeta/hija", map("y"));

        check("y tambien tienen backups", manager.getBackups("carpeta/hija").size() == 1);
        check("un id que escapa se rechaza antes de tocar nada", manager.getBackups("../../salir").isEmpty());
    }

    /* --- 6. the crusher has a limit that is a limit --- */

    static void crusherHasALimit() throws Exception
    {
        section("el troceador tiene un tope que es un tope");

        int limit = PacketCrusher.MAX_CHUNKS * PacketCrusher.BUFFER_SIZE;

        check("el limite por carga es holgado", limit > 8_000_000);
        check("el techo de memoria en vuelo es uno solo",
            PacketCrusher.MAX_PENDING_BYTES < PacketCrusher.MAX_PENDING * PacketCrusher.MAX_CHUNKS * PacketCrusher.BUFFER_SIZE);
    }

    static MapType map(String value)
    {
        MapType map = new MapType();

        map.put("marca", new StringType(value));

        return map;
    }

    /* --- 7. data of the wrong type is rejected instead of crashing the load --- */

    static void malformedDataDoesNotBreakTheLoad() throws Exception
    {
        section("los datos malformados no rompen la carga");

        Clips clips = new Clips("camera", null);

        check("una camara que no es una lista no rompe la carga", doesNotThrow(() -> clips.fromData(new IntType(5))));
        check("una camara ausente (null) no rompe la carga", doesNotThrow(() -> clips.fromData(null)));
        check("la camara malformada deja la pelicula sin clips", clips.get().isEmpty());

        ValueExpression expression = new ValueExpression("expression", new MathBuilder());

        check("una expresion que no es texto no rompe la carga", doesNotThrow(() -> expression.fromData(new IntType(5))));
        check("la expresion malformada se queda vacia", expression.get() == null);

        expression.fromData(new StringType("1 + 2"));

        check("una expresion de texto sigue funcionando", expression.get() != null);

        ListType keyframes = new ListType();
        MapType keyframe = new MapType();

        keyframe.putDouble("tick", 0D);
        keyframe.putDouble("value", 1D);
        keyframes.add(keyframe);

        MapType unknownType = new MapType();

        unknownType.putString("type", "tipo-inventado");
        unknownType.put("keyframes", keyframes);

        KeyframeChannel<Double> channel = new KeyframeChannel<>("channel", KeyframeFactories.DOUBLE);

        check("un tipo de keyframe desconocido no rompe la carga", doesNotThrow(() -> channel.fromData(unknownType)));
        check("un tipo desconocido deja la fabrica intacta", channel.getFactory() != null);
        check("un tipo desconocido no deja keyframes a medias", channel.getList().isEmpty());

        KeyframeChannel<Double> orphan = new KeyframeChannel<>("x", null);

        check("un canal sin fabrica (propiedad de un replay) no rompe la carga", doesNotThrow(() -> orphan.fromData(unknownType)));

        MapType knownType = new MapType();

        knownType.putString("type", "double");
        knownType.put("keyframes", keyframes);

        KeyframeChannel<Double> known = new KeyframeChannel<>("channel", KeyframeFactories.DOUBLE);

        known.fromData(knownType);

        check("un tipo conocido sigue leyendo los keyframes", known.getList().size() == 1);
        check("el keyframe leido conserva su valor", known.getList().get(0).getValue() == 1D);

        check("un enlace que no es texto ni mapa se rechaza", FilteredLink.from(new IntType(5)) == null);
        check("un enlace de texto sigue funcionando", FilteredLink.from(new StringType("mi-modelo")) != null);
        check("una multi lista que no es lista se rechaza", MultiLink.from(new IntType(5)) == null);

        ListType mixed = new ListType();

        mixed.add(new StringType("mi-modelo"));
        mixed.add(new IntType(5));

        MultiLink multi = MultiLink.from(mixed);

        check("un elemento malo de una multi lista se salta", multi != null && multi.children.size() == 1);
        check("una multi lista llamada con otra cosa no rompe la carga", doesNotThrow(() -> new MultiLink().fromData(new IntType(5))));

        MapType animation = new MapType();
        ListType translate = new ListType();

        translate.add(new IntType(5));
        animation.put("translate", translate);

        check("un keyframe de animacion que no es una lista no rompe la carga", doesNotThrow(() -> new AnimationPart(null).fromData(animation)));

        check("un payload ausente se lee como null", DataStorageUtils.readFromBytes(null) == null);
    }

    static boolean doesNotThrow(Runnable runnable)
    {
        try
        {
            runnable.run();

            return true;
        }
        catch (Throwable t)
        {
            System.out.println("       (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");

            return false;
        }
    }
}