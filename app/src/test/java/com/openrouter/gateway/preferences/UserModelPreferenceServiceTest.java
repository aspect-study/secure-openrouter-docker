package com.openrouter.gateway.preferences;

import com.openrouter.gateway.config.ModelConfig;
import com.openrouter.gateway.config.ModelConfigRepository;
import com.openrouter.gateway.exception.ModelAdminDisabledException;
import com.openrouter.gateway.exception.ModelNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link UserModelPreferenceService}.
 * No Spring context — Mockito only.
 * <p>
 * Covers all 8 cases specified in PRD-003 Phase D.
 */
@ExtendWith(MockitoExtension.class)
class UserModelPreferenceServiceTest {

    @Mock
    private ModelConfigRepository modelConfigRepository;

    @Mock
    private UserModelPreferenceRepository preferenceRepository;

    @InjectMocks
    private UserModelPreferenceService service;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final Long USER_ID = 42L;

    /** Builds a ModelConfig with the given id, modelId, and enabled state via reflection. */
    private ModelConfig modelConfig(Long id, String modelId, boolean enabled) {
        ModelConfig mc = new ModelConfig();
        // ModelConfig.id has no setter — set via reflection to keep entity clean
        try {
            var idField = ModelConfig.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mc, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mc.setModelId(modelId);
        mc.setEnabled(enabled);
        return mc;
    }

    /** Builds a UserModelPreference for a given modelId and enabled state. */
    private UserModelPreference preference(String modelId, boolean enabled) {
        UserModelPreference pref = new UserModelPreference();
        pref.setModelId(modelId);
        pref.setEnabled(enabled);
        return pref;
    }

    // ── Test data ─────────────────────────────────────────────────────────────

    private ModelConfig mc1; // admin-enabled
    private ModelConfig mc2; // admin-enabled
    private ModelConfig mc3; // admin-enabled
    private ModelConfig mc4; // admin-DISABLED

    @BeforeEach
    void setUp() {
        mc1 = modelConfig(1L, "nvidia/nemotron-nano-9b-v2:free",              true);
        mc2 = modelConfig(2L, "meta-llama/llama-3.3-70b-instruct:free",       true);
        mc3 = modelConfig(3L, "deepseek/deepseek-v4-flash:free",              true);
        mc4 = modelConfig(4L, "google/gemma-4-31b-it:free",                   false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case 1: No preference rows → all admin-enabled models returned
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEffectiveModels: no preference rows → all admin-enabled models returned as enabled")
    void getEffectiveModels_noPreferenceRows_allAdminEnabledReturned() {
        when(modelConfigRepository.findAllByOrderByModelIdAsc())
                .thenReturn(List.of(mc1, mc2, mc3, mc4));
        when(preferenceRepository.findByUserId(USER_ID))
                .thenReturn(List.of()); // no rows — sparse default = enabled

        UserModelsResponse response = service.getEffectiveModels(USER_ID, false);

        // All 3 admin-enabled models should be effectively enabled; mc4 (admin-disabled) is not
        assertThat(response.totalAdminEnabled()).isEqualTo(3);
        assertThat(response.totalUserEnabled()).isEqualTo(3);
        assertThat(response.models()).hasSize(4); // all models in list (dimmed admin-disabled ones too)

        assertThat(response.models())
                .filteredOn(UserModelDto::effectivelyEnabled)
                .hasSize(3)
                .extracting(UserModelDto::modelId)
                .containsExactlyInAnyOrder(
                        mc1.getModelId(), mc2.getModelId(), mc3.getModelId());

        // mc4 (admin-disabled) should be in list but not effectively enabled
        assertThat(response.models())
                .filteredOn(m -> m.modelId().equals(mc4.getModelId()))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.adminEnabled()).isFalse();
                    assertThat(m.effectivelyEnabled()).isFalse();
                    assertThat(m.userEnabled()).isTrue(); // default
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case 2: User disables 2 models → those 2 excluded from effectivelyEnabled
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEffectiveModels: user disables 2 models → those 2 not effectivelyEnabled")
    void getEffectiveModels_userDisablesTwoModels_twoExcluded() {
        when(modelConfigRepository.findAllByOrderByModelIdAsc())
                .thenReturn(List.of(mc1, mc2, mc3, mc4));
        when(preferenceRepository.findByUserId(USER_ID))
                .thenReturn(List.of(
                        preference(mc1.getModelId(), false), // user disabled mc1
                        preference(mc2.getModelId(), false)  // user disabled mc2
                ));

        UserModelsResponse response = service.getEffectiveModels(USER_ID, false);

        assertThat(response.totalAdminEnabled()).isEqualTo(3);
        assertThat(response.totalUserEnabled()).isEqualTo(1); // only mc3 remains

        assertThat(response.models())
                .filteredOn(UserModelDto::effectivelyEnabled)
                .singleElement()
                .extracting(UserModelDto::modelId)
                .isEqualTo(mc3.getModelId());

        assertThat(response.models())
                .filteredOn(m -> m.modelId().equals(mc1.getModelId()))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.userEnabled()).isFalse();
                    assertThat(m.effectivelyEnabled()).isFalse();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case 3: ROLE_ADMIN caller → all admin-enabled returned (preferences ignored)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEffectiveModels: ROLE_ADMIN caller → full admin-enabled list, preferences never consulted")
    void getEffectiveModels_adminCaller_allAdminEnabledReturnedPreferencesIgnored() {
        when(modelConfigRepository.findAllByOrderByModelIdAsc())
                .thenReturn(List.of(mc1, mc2, mc3, mc4));

        UserModelsResponse response = service.getEffectiveModels(USER_ID, true /* isAdmin */);

        // Admin sees all 3 admin-enabled models as effectively enabled
        assertThat(response.totalAdminEnabled()).isEqualTo(3);
        assertThat(response.totalUserEnabled()).isEqualTo(3);

        assertThat(response.models())
                .filteredOn(UserModelDto::effectivelyEnabled)
                .hasSize(3);

        // Preferences were NEVER consulted — verify no call to preferenceRepository
        verifyNoInteractions(preferenceRepository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case 4: toggleModel on admin-disabled model → ModelAdminDisabledException
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleModel: admin-disabled model → throws ModelAdminDisabledException (400)")
    void toggleModel_adminDisabledModel_throwsModelAdminDisabledException() {
        when(modelConfigRepository.findById(mc4.getId()))
                .thenReturn(Optional.of(mc4)); // mc4 is admin-disabled

        assertThatThrownBy(() -> service.toggleModel(USER_ID, mc4.getId()))
                .isInstanceOf(ModelAdminDisabledException.class)
                .hasMessageContaining("admin-disabled");

        // upsertToggle must NEVER be called when the guard rejects
        verifyNoInteractions(preferenceRepository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case 5: toggleModel with unknown modelConfigId → ModelNotFoundException
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleModel: unknown modelConfigId → throws ModelNotFoundException (404)")
    void toggleModel_unknownModelConfigId_throwsModelNotFoundException() {
        Long unknownId = 999L;
        when(modelConfigRepository.findById(unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleModel(USER_ID, unknownId))
                .isInstanceOf(ModelNotFoundException.class)
                .hasMessageContaining("999");

        verifyNoInteractions(preferenceRepository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case 6: toggleModel calls upsertToggle (not load-or-create) — verified via mock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleModel: uses atomic upsertToggle — never load-or-create (verified via mock interactions)")
    void toggleModel_usesAtomicUpsertToggle_notLoadOrCreate() {
        when(modelConfigRepository.findById(mc1.getId()))
                .thenReturn(Optional.of(mc1));

        // After upsert, re-fetch returns the toggled state (now disabled)
        when(preferenceRepository.findByUserIdAndModelId(USER_ID, mc1.getModelId()))
                .thenReturn(Optional.of(preference(mc1.getModelId(), false)));

        service.toggleModel(USER_ID, mc1.getId());

        // Verify upsertToggle was called with correct args
        ArgumentCaptor<Long>   userIdCaptor  = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> modelIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(preferenceRepository).upsertToggle(userIdCaptor.capture(), modelIdCaptor.capture());
        assertThat(userIdCaptor.getValue()).isEqualTo(USER_ID);
        assertThat(modelIdCaptor.getValue()).isEqualTo(mc1.getModelId());

        // save() must NEVER be called — confirms no load-or-create pattern
        verify(preferenceRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case 7: toggleModel idempotent — toggle twice restores original state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleModel: idempotent — toggle twice restores original (enabled) state")
    void toggleModel_toggleTwice_restoresOriginalState() {
        when(modelConfigRepository.findById(mc1.getId()))
                .thenReturn(Optional.of(mc1));

        // First toggle: upsert fires, re-fetch returns disabled (row inserted as b'0')
        when(preferenceRepository.findByUserIdAndModelId(USER_ID, mc1.getModelId()))
                .thenReturn(Optional.of(preference(mc1.getModelId(), false)))  // after 1st toggle
                .thenReturn(Optional.of(preference(mc1.getModelId(), true)));   // after 2nd toggle

        UserModelStatusDto firstResult  = service.toggleModel(USER_ID, mc1.getId());
        UserModelStatusDto secondResult = service.toggleModel(USER_ID, mc1.getId());

        assertThat(firstResult.userEnabled()).isFalse();
        assertThat(firstResult.effectivelyEnabled()).isFalse();

        assertThat(secondResult.userEnabled()).isTrue();
        assertThat(secondResult.effectivelyEnabled()).isTrue();

        // upsertToggle called exactly twice — once per toggle
        verify(preferenceRepository, times(2)).upsertToggle(USER_ID, mc1.getModelId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case 8: admin removes model → orphaned preference row silently excluded
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEffectiveModels: orphaned preference row (model removed from model_config) silently excluded")
    void getEffectiveModels_orphanedPreferenceRow_silentlyExcluded() {
        // model_config only has mc1 — mc2 was removed (simulating admin deleting a model row)
        when(modelConfigRepository.findAllByOrderByModelIdAsc())
                .thenReturn(List.of(mc1));

        // User has a preference row for mc2 (orphaned — model no longer in model_config)
        when(preferenceRepository.findByUserId(USER_ID))
                .thenReturn(List.of(
                        preference(mc1.getModelId(), true),
                        preference(mc2.getModelId(), false) // orphaned row
                ));

        UserModelsResponse response = service.getEffectiveModels(USER_ID, false);

        // Only mc1 appears in response — mc2's orphaned row is ignored (not in model_config)
        assertThat(response.models()).hasSize(1);
        assertThat(response.models().get(0).modelId()).isEqualTo(mc1.getModelId());
        assertThat(response.models().get(0).effectivelyEnabled()).isTrue();

        assertThat(response.totalAdminEnabled()).isEqualTo(1);
        assertThat(response.totalUserEnabled()).isEqualTo(1);
    }
}
