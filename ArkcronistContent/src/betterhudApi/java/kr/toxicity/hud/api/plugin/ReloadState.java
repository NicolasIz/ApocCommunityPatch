package kr.toxicity.hud.api.plugin;

import java.io.File;
import java.util.Map;

/** Signature of BetterHud 1.14.1's reload result, as far as ArkcronistContent uses it. Not packaged. */
public interface ReloadState {

    /** A finished build: the resource pack's files by path, and the folder they were written to. */
    record Success(Map<String, byte[]> resourcePack, File directory, long time) implements ReloadState {
    }

    record Failure(Throwable throwable) implements ReloadState {
    }

    final class OnReload implements ReloadState {
    }
}
