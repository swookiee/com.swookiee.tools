package com.swookiee.tools.com.swookiee.tools.deployplugin;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import com.swookiee.tools.client.SwookieClientBuilder;
import com.swookiee.tools.client.SwookieeClient;
import com.swookiee.tools.client.SwookieeClientException;

/**
 * 
 * This maven plugin executor serves as an intermediate layer between maven and the RFC-182 client. Via this tool we are
 * able to deploy jar files to a Swookiee instance.
 * 
 * @goal bundle-deploy
 * 
 */
public class SwookieeInstall extends AbstractMojo {

    /**
     * Hostname
     * 
     * @parameter default-value="localhost"
     */
    private String host;

    /**
     * HTTP port of swookiee instance.
     * 
     * @parameter default-value="8080"
     */
    private Integer port;

    /**
     * Admin Username.
     * 
     * @parameter default-value="admin"
     */
    private String username;

    /**
     * Admin Account Password.
     * 
     * @parameter default-value="admin123"
     */
    private String password;

    /**
     * Make use of Self signed https
     * 
     * @parameter default-value="false"
     */
    private boolean useHttps;

    /**
     * Enable self signed https
     * 
     * @parameter default-value="false"
     */
    private boolean useSelfSigned;

    /**
     * Do you want to deploy dependencies as well?
     * 
     * @parameter default-value="false"
     */
    private boolean deployDependencies;

    /**
     * The name of the generated JAR file.
     * 
     * @parameter default-value="${project.build.directory}/${project.build.finalName}.jar"
     * @required
     */
    private String bundleFile;

    /**
     * The set of dependencies required by the project
     * 
     * @parameter default-value="${project.dependencyArtifacts}"
     * @required
     * @readonly
     */
    private Set<Artifact> artifacts;

    /**
     * The set of dependencies required by the project
     * 
     * @parameter default-value="${project.dependencies}
     * @required
     * @readonly
     */
    private List<Dependency> dependencies;

    /** @component */
    private ArtifactResolver resolver;

    /** @parameter default-value="${localRepository}" */
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    /** @parameter default-value="${project.remoteArtifactRepositories}" */
    @SuppressWarnings("rawtypes")
    private List remoteRepositories;

    @Override
    public void execute() throws MojoExecutionException {
        try (SwookieeClient swookieeClient = getSwookieeClient()) {
            deployFile(swookieeClient, this.bundleFile);
            if (deployDependencies) {
                deployDependencies(swookieeClient);
            }
        } catch (final SwookieeClientException ex) {
            getLog().error("Could not deploy bundle: " + ex.getMessage(), ex);
            throw new MojoExecutionException("Could not deploy bundle: " + ex.getMessage(), ex);
        }
    }

    private void deployFile(SwookieeClient swookieeClient, String filePath) throws SwookieeClientException {

        if (filePath == null) {
            getLog().info("No Path to file given");
            return;
        }

        File bundle = new File(filePath);
        if (bundle != null && bundle.exists()) {
            forceInstallAndStartBundle(swookieeClient, bundle);
        } else {
            getLog().warn("Bundle '" + this.bundleFile + "' could not been found!");
        }
    }

    private void deployDependencies(SwookieeClient swookieeClient) throws SwookieeClientException {
        for (Dependency dependency : dependencies) {
            for (Artifact artifact : artifacts) {
                if (equalsDependencyArtefact(dependency, artifact)) {
                    try {
                        resolver.resolve(artifact, remoteRepositories, localRepository);
                        deployFile(swookieeClient, artifact.getFile().getPath());
                    } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
                        getLog().error("Could not resolve dependency: " + dependency.getArtifactId(), e);
                    }
                }
            }
        }
    }

    private boolean equalsDependencyArtefact(Dependency dependency, Artifact artifact) {
        return artifact.getGroupId().equals(dependency.getGroupId())
                && artifact.getArtifactId().equals(dependency.getArtifactId())
                && artifact.getVersion().equals(dependency.getVersion());
    }

    private SwookieeClient getSwookieeClient() throws SwookieeClientException {
        final SwookieClientBuilder swookieClientBuilder = SwookieClientBuilder.newTarget(this.host)
                .withPort(this.port)
                .withUsernamePassword(this.username, this.password);
        if (useSelfSigned) {
            swookieClientBuilder.enableSelfSignedHttps();
        }
        if (useHttps){
            swookieClientBuilder.enableHttps();
        }
        return swookieClientBuilder.create();
    }

    public void forceInstallAndStartBundle(final SwookieeClient swookieeClient, final File file)
            throws SwookieeClientException {
        getLog().info(
                String.format("Installing %s to %s", file.getAbsolutePath(), swookieeClient.getConfiguredTarget()));
        final String installedBundle = swookieeClient.installBundle(file, true);
        try{
            swookieeClient.startBundle(installedBundle);
        } catch (Exception ex) {
            getLog().warn("Could not start Bundle: " + file.toString());
        }

    }
}
