package me.usainsrht.basiceconomy.impl.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

@SuppressWarnings("UnstableApiUsage")
public class BasicEconomyLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(
                new RemoteRepository.Builder("mavenCentral", "default",
                        "https://repo.papermc.io/repository/maven-public/")
                        .build());

        // SQL Drivers
        resolver.addDependency(new Dependency(new DefaultArtifact("com.h2database:h2:2.2.224"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.mysql:mysql-connector-j:8.3.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.mariadb.jdbc:mariadb-java-client:3.3.3"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.postgresql:postgresql:42.7.3"), null));

        // MongoDB Driver
        resolver.addDependency(new Dependency(new DefaultArtifact("org.mongodb:mongodb-driver-sync:5.0.0"), null));

        // HikariCP
        resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:5.1.0"), null));

        resolver.addDependency(new Dependency(new DefaultArtifact("redis.clients:jedis:5.1.2"), null));

        classpathBuilder.addLibrary(resolver);
    }
}
