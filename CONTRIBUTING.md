# Contribution guide

If you'd like to submit a PR (pull request) to this repository, please carefully read following document. The failure to adhere to this document will result in immediate rejection of the PR.

These rules might feel tyrannical, but these rules ensure that the project stays maintainable for future years! The source code will turn into unmaintainable slop who will never support ever again (these rules weren't enforced in Blockbuster mod, and look what happened: it was abandoned).

This is a fork of [BBS mod](https://github.com/mchorse/bbs-mod). Upstream is archived, so pull requests go here and not there. See [README.md](README.md) for what that means.

## General principles

* No changes to `gradle` config (i.e. `gradle/`, `gradlew`, `gradlew.bat`, `gradle.properties`, and `build.gradle`), unless the pull request description explains why on a line starting with `gradle-justification:`. The CI rejects it otherwise.
* **Write your own code.** Taking an idea or a behaviour from another fork is fine. Copying its methods or classes, or cherry-picking its commits, is not. If a change comes from somewhere else, say so in the description and reimplement it here.
* **A user's data cannot be regenerated.** Films, models, keyframes, form ids, the `bbs` namespace and the `config/bbs` folder are on a player's disk and nowhere else. Any change that alters a saved format needs a migration and compatibility tests, and that is a design discussion before it is a patch.

## What you must not break

The mod declares `"environment": "*"`, so it runs on other people's servers and clients. A change that looks local can break someone else's world.

* **`src/main` is common code, `src/client` is client only.** A reference from common to client code crashes on a dedicated server, and the build will not tell you.
* **The network is client to server.** A new packet without a version bump cuts the connection of older clients already connected.
* **Mixins and the access widener are fragile.** They target internal Minecraft classes. A change there only shows up in game, never in a build.
* **The `bbs` namespace is part of the saved format.** It is written into the `type` field of every clip in every film. Renaming it makes resolution fail, the clip is dropped silently, and the film still opens with something missing. If it ever has to change, it needs an alias.
* **`config/bbs` does not move.** It is hardcoded, not derived from the mod id, which is exactly why changing it would take everyone's work with it.

## Code style

Here is a sample of code that adheres to all of the code style rules of this project:

```java
public static void main(String[] args) 
{
    List<String> strings = new ArrayList<>();
    int a = 10;

    /* a is used instead of c, for some reason */
    for (int i = 0; i < a; i++)
    {
        strings.add(String.valueOf(i));
    }
    
    float x = 10F;
    float y = 15.5F;
    float d = findDistance(0F, 0F, x, y);
    
    System.out.println("Distance between " + x + " and " + y + " is " + d + " meters!");
}

/**
 * Given two 2D points, calculate the distance between them
 */
private static float findDistance(float x1, float y1, float x2, float y2)
{
    float dx = x2 - x1;
    float dy = y2 - y1;
    
    return Math.sqrt(dx * dx + dy * dy);
}
```

Here we have:

### Organization

* The structure of any class must have the following order:
    * Fields: constants, static, instance
    * Static constructor
    * Static methods
    * Instance constructors
    * Methods
    * Nested classes, interfaces, enums and records
* Any **dead code** (check for the name being gray in IDE, or references) must be deleted!
* One line constructions are allowed only **if they look very similar** (look harmonious).
* Multiple `if` constructions in a row must be separated with a new line to not mistake it for `else if`s!
* Blocks of variable definitions must be cluttered together (first go objects then primitives, unless it's impossible due to algorithm).
* Blocks of constructions, variable definitions, and method invocation must be separated by two new lines.
* Code repetition/duplication must be avoided! Make sure to check *Utils classes and JOML for any relevant methods.

### Formatting

* `{` is always on the next line.
* All instance method calls and field references must have `this`!
* Try to stay under 150 LOC methods. Refactor accordingly to keep method size small!

### Types

* No generic type provided where can be omitted (i.e. `new ArrayList<>()`).
* Float `1F`, double `2D` and long `3L` number specifiers must be in capital letters always, and if there is no decimal, period must be absent!
* No full references to the classes in the code (i.e. `new org.joml.Vector3f()`, unless there are conflicting names)!
* No `var`!

### Comments

* All comments must be in **English**!
* All comments in the body of the code must be within `/* ... */` and never with `//`.
* Self-explanatory comments must be avoided.
* JavaDocs comments must be present only above the method or class definition, but prior to any **annotations**!

## Checks

`checks/RunChecks.java` holds the checks that protect the fixes in this fork: the
atomic film save, path containment, the limits read from a stream, the factory
error messages, the backup list and restore, and the chunker's limits.

They run against the **real compiled classes**, not against a copy of the rules
they test, because a check that reimplements the rule cannot fail when the rule
breaks. That is exactly how a regression slipped through before.

```bash
./gradlew build
./gradlew -I checks/cp-init.gradle printRuntimeCp
java -cp "build/classes/java/main:build/classes/java/client:$(cat build/cp.txt)" checks/RunChecks.java
```

The CI runs them on every pull request, next to the build. They need a JDK, not
the game.

## Pull requests

`main` is protected: nothing reaches it except through a pull request, not even a maintainer.

Every pull request has to explain three things, and the CI enforces the rest:

1. **The problem.** What is wrong or missing, and why it matters.
2. **The change.** What you did, and what you deliberately did not do.
3. **The verification.** The exact commands you ran and what they printed.

Before it can be merged, `build`, `public surface` and `gradle config untouched` all have to be green.

**Say what you could not check.** This mod touches rendering, keyboard, mixins and networking, and a green build only proves the Java parses. If you did not open Minecraft and see it work, write that it was not checked and say what would be needed. A pull request that claims "works" without that evidence gets rejected, because the claim is indistinguishable from a guess.

Commits are small, coherent and in English, with a conventional subject:

```text
fix(forms): no dibujar la punta cuando el grosor es cero
feat(panel): agrupar los ajustes por forma y no por tipo
refactor(film): extraer el cálculo de eased value a FilmUtils
```

One commit, one reason. Branch from `main`.