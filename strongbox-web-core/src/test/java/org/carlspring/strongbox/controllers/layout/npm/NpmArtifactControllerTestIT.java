package org.carlspring.strongbox.controllers.layout.npm;

import org.carlspring.strongbox.artifact.coordinates.NpmArtifactCoordinates;
import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.domain.ArtifactEntry;
import org.carlspring.strongbox.domain.RemoteArtifactEntry;
import org.carlspring.strongbox.providers.layout.NpmLayoutProvider;
import org.carlspring.strongbox.rest.common.NpmRestAssuredBaseTest;
import org.carlspring.strongbox.services.ArtifactEntryService;
import org.carlspring.strongbox.storage.repository.NpmRepositoryFactory;
import org.carlspring.strongbox.storage.repository.MutableRepository;
import org.carlspring.strongbox.storage.repository.RepositoryPolicyEnum;
import org.carlspring.strongbox.storage.repository.RepositoryTypeEnum;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@IntegrationTest
@RunWith(SpringJUnit4ClassRunner.class)
public class NpmArtifactControllerTestIT
        extends NpmRestAssuredBaseTest
{

    private static final String REPOSITORY_RELEASES = "nactit-releases";

    private static final String REPOSITORY_PROXY = "nactit-npm-proxy";

    private static final String REPOSITORY_GROUP = "nactit-npm-group";

    @Inject
    private NpmRepositoryFactory npmRepositoryFactory;

    @Inject
    @Qualifier("contextBaseUrl")
    private String contextBaseUrl;

    @Inject
    private ArtifactEntryService artifactEntryService;

    @BeforeClass
    public static void cleanUp()
        throws Exception
    {
        cleanUp(getRepositoriesToClean());
    }

    public static Set<MutableRepository> getRepositoriesToClean()
    {
        Set<MutableRepository> repositories = new LinkedHashSet<>();
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_RELEASES, NpmLayoutProvider.ALIAS));
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_PROXY, NpmLayoutProvider.ALIAS));
        repositories.add(createRepositoryMock(STORAGE0, REPOSITORY_GROUP, NpmLayoutProvider.ALIAS));

        return repositories;
    }

    @Before
    public void init()
        throws Exception
    {
        super.init();

        MutableRepository repository1 = npmRepositoryFactory.createRepository(REPOSITORY_RELEASES);
        repository1.setPolicy(RepositoryPolicyEnum.RELEASE.getPolicy());

        createRepository(STORAGE0, repository1);

        //noinspection ResultOfMethodCallIgnored
        Files.createDirectories(Paths.get(TEST_RESOURCES));

        // createFile(repository1, "org/foo/bar/blah.gz");

        createProxyRepository(STORAGE0,
                              REPOSITORY_PROXY,
                              "https://registry.npmjs.org/");

        MutableRepository repository2 = npmRepositoryFactory.createRepository(REPOSITORY_GROUP);
        repository2.setType(RepositoryTypeEnum.GROUP.getType());
        repository2.setGroupRepositories(Sets.newHashSet(STORAGE0 + ":" + REPOSITORY_PROXY));

        createRepository(STORAGE0, repository2);
    }

    @After
    public void removeRepositories()
            throws IOException, JAXBException
    {
        removeRepositories(getRepositoriesToClean());
    }

    /**
     * Note: This test requires an Internet connection.
     *
     * @throws Exception
     */
    @Test
    public void testResolveArtifactViaProxy()
            throws Exception
    {
        // https://registry.npmjs.org/compression/-/compression-1.7.2.tgz
        String artifactPath = "/storages/" + STORAGE0 + "/" + REPOSITORY_PROXY + "/" +
                              "compression/-/compression-1.7.2.tgz";

        resolveArtifact(artifactPath);
    }

    /**
     * Note: This test requires an Internet connection.
     *
     * @throws Exception
     */
    @Test
    public void testResolveArtifactViaGroupWithProxy()
            throws Exception
    {
        // https://registry.npmjs.org/compression/-/compression-1.7.2.tgz
        String artifactPath = "/storages/" + STORAGE0 + "/" + REPOSITORY_GROUP + "/" +
                              "compression/-/compression-1.7.2.tgz";

        resolveArtifact(artifactPath);
    }

    @Test
    public void testViewArtifactViaProxy() 
            throws Exception
    {
        NpmArtifactCoordinates c = NpmArtifactCoordinates.of("react", "16.5.0");
        
        given().header("User-Agent", "npm/*")
               .when()
               .get(contextBaseUrl + "/storages/" + STORAGE0 + "/" + REPOSITORY_PROXY + "/" +
                       c.getId())
               .peek()
               .then()
               .statusCode(HttpStatus.OK.value())
               .and()
               .body("name", CoreMatchers.equalTo("react"))
               .body("versions.size()", Matchers.greaterThan(0));
        
        ArtifactEntry artifactEntry = artifactEntryService.findOneArtifact(STORAGE0, REPOSITORY_PROXY, c.toPath());
        assertNotNull(artifactEntry);
        assertTrue(artifactEntry instanceof RemoteArtifactEntry);
        assertFalse(((RemoteArtifactEntry)artifactEntry).getIsCached());
    }
    
}
