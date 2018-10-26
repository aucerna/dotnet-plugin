package net.karlmartens.dotnet;

import org.apache.tools.ant.DirectoryScanner;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarOutputStream;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class Distribution extends DotnetDefaultTask  {
    private static Logger LOGGER = Logging.getLogger(Distribution.class);

    private String[]_runtimes = new String[0];
    private String[] _includes = new String[] {"**/publish"};
    private String[] _excludes = new String[] {"*.tests/**"};
    private boolean _followSymlinks = false;
    private String _basename;
    private String _version;

    public void setRuntimes(String... runtimes) {
        _runtimes = runtimes;
    }

    public String[] getRuntimes() {
        return _runtimes;
    }

    public void setIncludes(String... includes) {
        _includes = includes;
    }

    public String[] getIncludes() {
        return _includes;
    }

    public void setExcludes(String... excludes) {
        _excludes = excludes;
    }

    public String[] getExcludes() {
        return _excludes;
    }

    public void setFollowSymlinks(boolean followSymlinks) {
        _followSymlinks = followSymlinks;
    }

    public boolean getFollowSymlinks() {
        return _followSymlinks;
    }

    public void setBasename(String basename) {
        _basename = basename;
    }

    public String getBasename() {
        return _basename;
    }

    public void setVersion(String version) {
        _version = version;
    }

    public String getVersion() {
        return _version;
    }

    @OutputDirectory
    public File getOutputDir() {
        return getProject().getBuildDir().toPath().resolve("dist").toFile();
    }

    @TaskAction
    void exec() throws Exception {
        if (_runtimes.length > 0) {
            for (String runtime : _runtimes) {
                publish(runtime);
            }
        } else {
            DotnetExtension ext = getExtension();
            publish(ext.getRuntime());
        }

        Path projectDir = getProject().getProjectDir().toPath();

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(projectDir.toFile());
        scanner.setIncludes(_includes);
        scanner.setExcludes(_excludes);
        scanner.setFollowSymlinks(_followSymlinks);
        scanner.scan();

        for (String file : scanner.getIncludedDirectories()) {
            archive(projectDir.resolve(file).toFile());
        }
    }

    private void archive(File file) throws IOException {
        LOGGER.quiet("Bundling {}.", file.toString());
        Path source = file.toPath();

        Path target = generateArchiveTarget(source);
        LOGGER.quiet("Archive {}.", target.toString());

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(file);
        scanner.setIncludes(new String[] {"**/*"});
        scanner.setFollowSymlinks(true);
        scanner.scan();

        File targetFile = target.toFile();
        targetFile.createNewFile();

        try (TarOutputStream out = new TarOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile, false))))) {
            for (String includedFile : scanner.getIncludedFiles()) {
                LOGGER.debug("Adding file {}", includedFile);
                File f = source.resolve(includedFile).toFile();

                out.putNextEntry(new TarEntry(f, includedFile));

                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
                    int count;
                    byte data[] = new byte[2048];
                    while ((count = in.read(data)) != -1) {
                        out.write(data, 0, count);
                    }
                }
            }
        }
    }

    private Path generateArchiveTarget(Path source) {
        StringBuilder filename = new StringBuilder();
        if (_basename != null)
            filename.append(_basename);

        if (filename.length() > 0)
            filename.append("-");

        filename.append(source.getParent().getFileName().toString());

        if (_version != null && _version.length() > 0) {
            if (filename.length() > 0)
                filename.append("-");

            filename.append(_version);
        }

        filename.append(".tar.gz");

        Path baseDir = getOutputDir().toPath();
        return baseDir.resolve(filename.toString());
    }

    private void publish(String runtime) {
        getProject().exec(execSpec -> {
            DotnetExtension ext = getExtension();
            execSpec.setExecutable(ext.getExecutable());

            List<String> args = new ArrayList<>();
            args.add("publish");
            whenHasValue(ext.getSolution(), args::add);
            whenHasValue(ext.getConfiguration(), addNamedParameter(args, "--configuration"));
            whenHasValue(ext.getFramework(), addNamedParameter(args, "--framework"));
            whenHasValue(runtime, addNamedParameter(args, "--runtime"));
            appendParameters(args);

            execSpec.setArgs(args);
        });
    }

}
