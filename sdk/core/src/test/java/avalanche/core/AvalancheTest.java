package avalanche.core;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Set;

import avalanche.core.utils.AvalancheLog;
import avalanche.core.utils.IdHelper;
import avalanche.core.utils.StorageHelper;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Constants.class, AvalancheLog.class, StorageHelper.class, IdHelper.class})
public class AvalancheTest {

    private static final String DUMMY_APP_KEY = "123e4567-e89b-12d3-a456-426655440000";

    private Application application;
    private Context context;

    @Before
    public void setUp() throws Exception {
        application = mock(Application.class);
        context = mock(Context.class);

        when(application.getApplicationContext()).thenReturn(context);

        PowerMockito.mockStatic(Constants.class);
        PowerMockito.mockStatic(AvalancheLog.class);
        PowerMockito.mockStatic(StorageHelper.class);
        PowerMockito.mockStatic(IdHelper.class);
    }

    @Test
    public void avalancheInstanceTest() {
        assertNotNull(Avalanche.getSharedInstance());
    }

    @Test
    public void avalancheUseDefaultFeaturesTest() {
        Avalanche.useFeatures(application, DUMMY_APP_KEY, (AvalancheFeature) null);

        // Verify that no modules have been auto-loaded since none are configured for this
        assertEquals(0, Avalanche.getSharedInstance().getFeatures().size());
        assertEquals(application, Avalanche.getSharedInstance().getApplication());
    }

    @Test
    public void avalancheUseDummyFeatureTest() {
        Avalanche.useFeatures(application, DUMMY_APP_KEY, DummyFeature.class);

        // Verify that single module has been loaded and configured
        assertEquals(1, Avalanche.getSharedInstance().getFeatures().size());
        assertTrue(Avalanche.getSharedInstance().getFeatures().contains(DummyFeature.getInstance()));
    }

    @Test
    public void avalancheUseDummyFeaturesTest() {
        Avalanche.useFeatures(application, DUMMY_APP_KEY, DummyFeature.class, AnotherDummyFeature.class);

        // Verify that the right amount of modules have been loaded and configured
        assertEquals(2, Avalanche.getSharedInstance().getFeatures().size());
        assertTrue(Avalanche.getSharedInstance().getFeatures().contains(DummyFeature.getInstance()));
        assertTrue(Avalanche.getSharedInstance().getFeatures().contains(AnotherDummyFeature.getInstance()));
    }

    @Test
    public void avalancheAddFeaturesTest() {
        Avalanche.useFeatures(application, DUMMY_APP_KEY, (AvalancheFeature) null);

        // Verify that no initial modules are loaded and configured
        assertEquals(0, Avalanche.getSharedInstance().getFeatures().size());

        Avalanche.getSharedInstance().addFeature(DummyFeature.getInstance());
        assertEquals(1, Avalanche.getSharedInstance().getFeatures().size());
        assertTrue(Avalanche.getSharedInstance().getFeatures().contains(DummyFeature.getInstance()));

        // Verify that adding a module only gets added once
        Avalanche.getSharedInstance().addFeature(DummyFeature.getInstance());
        assertEquals(1, Avalanche.getSharedInstance().getFeatures().size());

        Avalanche.getSharedInstance().addFeature(AnotherDummyFeature.getInstance());
        assertEquals(2, Avalanche.getSharedInstance().getFeatures().size());
        assertTrue(Avalanche.getSharedInstance().getFeatures().contains(DummyFeature.getInstance()));
        assertTrue(Avalanche.getSharedInstance().getFeatures().contains(AnotherDummyFeature.getInstance()));
    }

    @Test
    public void avalancheFeaturesEnableTest() {
        Avalanche.useFeatures(application, DUMMY_APP_KEY, DummyFeature.class, AnotherDummyFeature.class);

        // Verify modules are enabled by default
        Avalanche avalanche = Avalanche.getSharedInstance();
        Set<AvalancheFeature> features = avalanche.getFeatures();

        assertTrue(Avalanche.isEnabled());
        for (AvalancheFeature feature : features) {
            assertTrue(feature.isEnabled());
        }

        assertTrue(Avalanche.getSharedInstance().isFeatureEnabled(DummyFeature.class));
        assertTrue(Avalanche.getSharedInstance().isFeatureEnabled(AnotherDummyFeature.class));
        assertFalse(Avalanche.getSharedInstance().isFeatureEnabled(InvalidFeature.class));

        // Verify disabling base disables all modules
        Avalanche.setEnabled(false);

        assertFalse(Avalanche.isEnabled());
        for (AvalancheFeature feature : features) {
            assertFalse(feature.isEnabled());
        }

        assertFalse(Avalanche.getSharedInstance().isFeatureEnabled(DummyFeature.class));
        assertFalse(Avalanche.getSharedInstance().isFeatureEnabled(AnotherDummyFeature.class));

        // Verify re-enabling base re-enables all modules
        Avalanche.setEnabled(true);
        assertTrue(Avalanche.isEnabled());
        for (AvalancheFeature feature : features) {
            assertTrue(feature.isEnabled());
        }

        // Verify that disabling one module leaves base and other modules enabled
        DummyFeature.getInstance().setEnabled(false);
        assertFalse(DummyFeature.getInstance().isEnabled());
        assertTrue(Avalanche.getSharedInstance().isEnabled());
        assertTrue(AnotherDummyFeature.getInstance().isEnabled());
    }

    @Test(expected = IllegalArgumentException.class)
    public void avalancheInvalidFeatureTest() {
        Avalanche.useFeatures(application, DUMMY_APP_KEY, InvalidFeature.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void avalancheNullApplicationTest() {
        Avalanche.useFeatures(null, DUMMY_APP_KEY, DummyFeature.class);
    }

    @Test
    @Ignore //FIXME broken when running inside AS
    public void avalancheNullAppIdentifierTest() {
        Avalanche.useFeatures(application, null, DummyFeature.class);

        PowerMockito.verifyStatic();
        AvalancheLog.error(anyString());
    }

    @Test
    public void avalancheEmptyAppIdentifierTest() {
        Avalanche.useFeatures(application, "", DummyFeature.class);

        PowerMockito.verifyStatic();
        AvalancheLog.error(anyString(), any(IllegalArgumentException.class));
    }

    @Test
    public void avalancheTooShortAppIdentifierTest() {
        Avalanche.useFeatures(application, "too-short", DummyFeature.class);

        PowerMockito.verifyStatic();
        AvalancheLog.error(anyString(), any(IllegalArgumentException.class));
    }

    @Test
    public void avalancheInvalidAppIdentifierTest() {
        Avalanche.useFeatures(application, "123xyz12-3xyz-123x-yz12-3xyz123xyz12", DummyFeature.class);
        PowerMockito.verifyStatic();
        AvalancheLog.error(anyString(), any(NumberFormatException.class));
    }

    static class DummyFeature extends AbstractAvalancheFeature {

        private static DummyFeature sharedInstance = null;

        public static DummyFeature getInstance() {
            if (sharedInstance == null) {
                sharedInstance = new DummyFeature();
            }
            return sharedInstance;
        }

    }

    static class AnotherDummyFeature extends AbstractAvalancheFeature {

        private static AnotherDummyFeature sharedInstance;

        public static AnotherDummyFeature getInstance() {
            if (sharedInstance == null) {
                sharedInstance = new AnotherDummyFeature();
            }
            return sharedInstance;
        }
    }

    static class InvalidFeature extends AbstractAvalancheFeature {

    }


}