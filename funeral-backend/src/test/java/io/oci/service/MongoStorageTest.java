package io.oci.service;

import java.util.List;

import io.oci.model.Blob;
import io.oci.model.Manifest;
import io.oci.model.Repository;
import io.oci.model.RepositoryPermission;
import io.oci.model.User;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@QuarkusTest
@QuarkusTestResource(
    MongoTestResource.class
)
public class MongoStorageTest {

    @Inject
    MongoManifestStorage manifestStorage;

    @Inject
    MongoRepositoryStorage repositoryStorage;

    @Inject
    MongoUserStorage userStorage;

    @Inject
    MongoBlobStorage blobStorage;

    @Inject
    MongoRepositoryPermissionStorage permissionStorage;

    @Inject
    @jakarta.inject.Named(
        "userStorage"
    )
    UserStorage producedUserStorage;

    @BeforeEach
    public void checkDocker() {
        assumeTrue(
                MongoTestResource.dockerAvailable,
                "Docker is not available, skipping MongoDB integration tests"
        );
    }

    private Manifest newManifest(
            String repositoryName,
            String tag,
            String digest
    ) {
        Manifest manifest = new Manifest();
        manifest.repositoryName = repositoryName;
        manifest.tag = tag;
        manifest.digest = digest;
        manifest.mediaType = "application/vnd.docker.distribution.manifest.v2+json";
        manifest.content = "{}";
        manifest.contentLength = 2L;
        return manifest;
    }

    @Test
    public void testManifestCrudAndQueries() {
        String repo = "mongo-it/manifest-crud";
        Manifest m1 = newManifest(
                repo,
                "v1",
                "sha256:it-digest-1"
        );
        manifestStorage.persist(
                m1
        );
        assertNotNull(
                m1.id
        );
        assertNotNull(
                m1.createdAt
        );
        assertNotNull(
                m1.updatedAt
        );

        Manifest found = manifestStorage.findById(
                m1.id
        );
        assertNotNull(
                found
        );
        assertEquals(
                repo,
                found.repositoryName
        );

        assertNotNull(
                manifestStorage.findByRepositoryAndDigest(
                        repo,
                        "sha256:it-digest-1"
                )
        );
        assertNotNull(
                manifestStorage.findByRepositoryAndTag(
                        repo,
                        "v1"
                )
        );
        assertNull(
                manifestStorage.findByRepositoryAndTag(
                        repo,
                        "missing"
                )
        );

        // Update existing manifest (persistOrUpdate path with id set)
        m1.contentLength = 42L;
        manifestStorage.persist(
                m1
        );
        assertEquals(
                42L,
                manifestStorage.findById(
                        m1.id
                ).contentLength
        );

        Manifest m2 = newManifest(
                repo,
                "v2",
                "sha256:it-digest-2"
        );
        m2.artifactType = "application/vnd.example+type";
        m2.subject = new Manifest.Subject(
                "sha256:it-digest-1",
                "application/vnd.docker.distribution.manifest.v2+json",
                10L
        );
        manifestStorage.persist(
                m2
        );

        assertEquals(
                2,
                manifestStorage.findByRepository(
                        repo
                ).size()
        );
        assertEquals(
                2,
                manifestStorage.countByRepository(
                        repo
                )
        );
        assertTrue(
                manifestStorage.listAll().size() >= 2
        );

        List<Manifest> referrers = manifestStorage.findBySubjectDigest(
                repo,
                "sha256:it-digest-1"
        );
        assertEquals(
                1,
                referrers.size()
        );
        assertEquals(
                1,
                manifestStorage.findBySubjectDigestAndArtifactType(
                        repo,
                        "sha256:it-digest-1",
                        "application/vnd.example+type"
                ).size()
        );
        assertEquals(
                0,
                manifestStorage.findBySubjectDigestAndArtifactType(
                        repo,
                        "sha256:it-digest-1",
                        "application/vnd.other+type"
                ).size()
        );

        List<String> tags = manifestStorage.findTagsByRepository(
                repo,
                null,
                10
        );
        assertTrue(
                tags.contains(
                        "v1"
                )
        );
        assertTrue(
                tags.contains(
                        "v2"
                )
        );

        List<Manifest> paged = manifestStorage.findByRepositoryAndTagList(
                repo,
                null,
                1
        );
        assertEquals(
                1,
                paged.size()
        );

        manifestStorage.deleteByRepositoryAndTag(
                repo,
                "v2"
        );
        assertNull(
                manifestStorage.findByRepositoryAndTag(
                        repo,
                        "v2"
                )
        );

        manifestStorage.delete(
                m1.id
        );
        assertNull(
                manifestStorage.findById(
                        m1.id
                )
        );
    }

    @Test
    public void testRepositoryCrud() {
        String name = "mongo-it/repo-crud";
        Repository repository = new Repository(
                name
        );
        repositoryStorage.persist(
                repository
        );

        Repository found = repositoryStorage.findByName(
                name
        );
        assertNotNull(
                found
        );
        assertEquals(
                name,
                found.name
        );
        assertTrue(
                repositoryStorage.count() >= 1
        );
        assertTrue(
                repositoryStorage.listAll()
                        .stream()
                        .anyMatch(
                                r -> name.equals(
                                        r.name
                                )
                        )
        );
        assertFalse(
                repositoryStorage.findByNameWithMultipleEntries(
                        name
                ).isEmpty()
        );

        repositoryStorage.deleteByName(
                name
        );
        assertNull(
                repositoryStorage.findByName(
                        name
                )
        );
    }

    @Test
    public void testUserCrud() {
        User user = new User();
        user.username = "mongo-it-user";
        user.passwordHash = "hash";
        user.email = "it@example.com";
        user.roles = List.of(
                "USER"
        );
        userStorage.persist(
                user
        );
        assertNotNull(
                user.id
        );

        User found = userStorage.findByUsername(
                "mongo-it-user"
        );
        assertNotNull(
                found
        );
        assertEquals(
                "it@example.com",
                found.email
        );
        assertNotNull(
                userStorage.findById(
                        user.id
                )
        );
        assertTrue(
                userStorage.listAll()
                        .stream()
                        .anyMatch(
                                u -> "mongo-it-user".equals(
                                        u.username
                                )
                        )
        );

        userStorage.deleteByUsername(
                "mongo-it-user"
        );
        assertNull(
                userStorage.findByUsername(
                        "mongo-it-user"
                )
        );
    }

    @Test
    public void testBlobCrud() {
        Blob blob = new Blob();
        blob.digest = "sha256:mongo-it-blob";
        blob.contentLength = 100L;
        blob.mediaType = "application/octet-stream";
        blobStorage.persist(
                blob
        );
        assertNotNull(
                blob.id
        );

        Blob found = blobStorage.findByDigest(
                "sha256:mongo-it-blob"
        );
        assertNotNull(
                found
        );
        assertEquals(
                100L,
                found.contentLength
        );

        // Update path (id already set)
        blob.contentLength = 200L;
        blobStorage.persist(
                blob
        );
        assertEquals(
                200L,
                blobStorage.findByDigest(
                        "sha256:mongo-it-blob"
                ).contentLength
        );

        blobStorage.deleteByDigest(
                "sha256:mongo-it-blob"
        );
        assertNull(
                blobStorage.findByDigest(
                        "sha256:mongo-it-blob"
                )
        );

        Blob blob2 = new Blob();
        blob2.digest = "sha256:mongo-it-blob-2";
        blobStorage.persist(
                blob2
        );
        blobStorage.delete(
                blob2.id
        );
        assertNull(
                blobStorage.findByDigest(
                        "sha256:mongo-it-blob-2"
                )
        );
    }

    @Test
    public void testRepositoryPermissionCrudAndChecks() {
        String username = "mongo-it-perm-user";
        String repo = "mongo-it/perm-repo";

        // MongoRepositoryPermissionStorage resolves users through the
        // produced ("userStorage") bean, so persist lookup users there
        User user = new User();
        user.username = username;
        user.roles = List.of(
                "USER"
        );
        producedUserStorage.persist(
                user
        );

        // No permission yet
        assertFalse(
                permissionStorage.hasPullPermission(
                        username,
                        repo
                )
        );
        assertFalse(
                permissionStorage.hasPushPermission(
                        username,
                        repo
                )
        );

        RepositoryPermission permission = new RepositoryPermission(
                username,
                repo
        );
        permission.canPull = true;
        permission.canPush = false;
        permissionStorage.persist(
                permission
        );

        assertNotNull(
                permissionStorage.findByUsernameAndRepository(
                        username,
                        repo
                )
        );
        assertEquals(
                1,
                permissionStorage.findByUsername(
                        username
                ).size()
        );
        assertEquals(
                1,
                permissionStorage.findByRepository(
                        repo
                ).size()
        );
        assertTrue(
                permissionStorage.listAll().size() >= 1
        );
        assertTrue(
                permissionStorage.hasPullPermission(
                        username,
                        repo
                )
        );
        assertFalse(
                permissionStorage.hasPushPermission(
                        username,
                        repo
                )
        );

        // Admin has all permissions regardless of entries
        User admin = new User();
        admin.username = "mongo-it-perm-admin";
        admin.roles = List.of(
                "ADMIN"
        );
        producedUserStorage.persist(
                admin
        );
        assertTrue(
                permissionStorage.hasPullPermission(
                        "mongo-it-perm-admin",
                        repo
                )
        );
        assertTrue(
                permissionStorage.hasPushPermission(
                        "mongo-it-perm-admin",
                        repo
                )
        );

        permissionStorage.deleteByUsernameAndRepository(
                username,
                repo
        );
        assertNull(
                permissionStorage.findByUsernameAndRepository(
                        username,
                        repo
                )
        );

        RepositoryPermission p2 = new RepositoryPermission(
                username,
                "mongo-it/perm-repo-2"
        );
        permissionStorage.persist(
                p2
        );
        permissionStorage.deleteByUsername(
                username
        );
        assertTrue(
                permissionStorage.findByUsername(
                        username
                ).isEmpty()
        );

        userStorage.deleteByUsername(
                username
        );
        userStorage.deleteByUsername(
                "mongo-it-perm-admin"
        );
        producedUserStorage.deleteByUsername(
                username
        );
        producedUserStorage.deleteByUsername(
                "mongo-it-perm-admin"
        );
    }
}
