package cz.inovatika.touchstore.impl;

import cz.inovatika.conf.TouchStoreConfig;
import cz.inovatika.touchstore.TouchStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TouchStoreImpl implements TouchStore {

    public static final String PID_FILE="pid.txt";
    public static final String ROOT_PID_FILE ="root_pid.txt";
    public static final String MODS_FILE = "mods.xml";
    public static final String MD5_FILE = "mods_md5.txt";
    public static final String SOURCE_FILE = "source.txt";
    public static final String PHYSICAL_LOCATIONS = "physicalLocations.txt";

    private final TouchStoreConfig touchStoreConfig;

    public TouchStoreImpl(TouchStoreConfig touchStoreConfig) {
        this.touchStoreConfig = touchStoreConfig;
    }

    public TouchStoreConfig getTouchStoreConfig() {
        return touchStoreConfig;
    }

    private Path calculatePath(String pid) {
        String basePath = this.touchStoreConfig.getBaseDir();
        String cleanUuid = pid.replace("uuid:", "").replaceAll("-", "");
        String dir1 = cleanUuid.substring(0, Math.min(4, cleanUuid.length()));
        String dir2 = cleanUuid.length() > 4 ? cleanUuid.substring(4, Math.min(8, cleanUuid.length())) : null;
        String dir3 = cleanUuid.length() > 8 ? cleanUuid.substring(8, Math.min(10,cleanUuid.length())) : null;

        if (dir2 == null) {
            return Paths.get(basePath,  dir1);
        }
        if (dir3 == null) {
            return Paths.get(basePath, dir1,  dir2);
        }
        if (dir2 != null && dir3 != null) {
            return Paths.get(basePath, dir1,  dir2, dir3);
        }

        return null;
    }

    @Override
    public void touch(String pid, String md5, String source, String rootPid, List<String> physicalLocations) throws IOException {
        Path path = calculatePath(pid);
        Path createdPath = Files.createDirectories(path);

        Path md5File = createdPath.resolve(MD5_FILE);
        Files.write(md5File, md5.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Path sourceFile = createdPath.resolve(SOURCE_FILE);
        Files.write(sourceFile, source.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Path physicalLocationsfile = createdPath.resolve(PHYSICAL_LOCATIONS);
        String collect = physicalLocations.stream().collect(Collectors.joining("\n"));
        Files.write(physicalLocationsfile,  collect.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Path pidFile = createdPath.resolve(PID_FILE);
        Files.write(pidFile,  pid.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Path rootPidFile = createdPath.resolve(ROOT_PID_FILE);
        Files.write(rootPidFile,  rootPid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public void storeMods(String pid, String mods) throws IOException {
        Path path = calculatePath(pid);
        Path createdPath = Files.createDirectories(path);

        Path modsFile = createdPath.resolve(MODS_FILE);
        Files.write(modsFile,  mods.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public boolean exists(String pid) {
        Path path = calculatePath(pid);
        Path filePath = path.resolve(MD5_FILE);
        return Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    @Override
    public boolean isModsChanged(String pid, String md5) throws IOException {
        if (exists(pid)) {
            String storedMd5 = getMd5(pid);
            return !md5.equals(storedMd5);
        }
        return false;
    }

    @Override
    public String getMd5(String pid) throws IOException {
        Path path = calculatePath(pid);
        Path filePath = path.resolve(MD5_FILE);
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } else return null;
    }

    @Override
    public List<String> getPhysicalLocations(String pid) throws IOException {
        Path path = calculatePath(pid);
        Path filePath = path.resolve(PHYSICAL_LOCATIONS);
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            return Files.readAllLines(filePath, StandardCharsets.UTF_8);
        } else return new ArrayList<>();
    }

    @Override
    public String getRootPid(String pid) throws IOException {
        Path path = calculatePath(pid);
        Path filePath = path.resolve(ROOT_PID_FILE);
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } else return null;
    }
}
