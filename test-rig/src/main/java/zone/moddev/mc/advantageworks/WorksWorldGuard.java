package zone.moddev.mc.advantageworks;

import java.nio.file.Path;

final class WorksWorldGuard {
    private WorksWorldGuard() {}

    static boolean authorized(Path world, boolean markerPresent) {
        if (!markerPresent || world == null) return false;
        Path name = world.toAbsolutePath().normalize().getFileName();
        return name != null && name.toString().startsWith("AdvantageWorks");
    }
}
