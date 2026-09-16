package com.starskyxiii.collapsible_groups.config;

import com.starskyxiii.collapsible_groups.Constants;

public final class SettingsController {
    public interface Storage {
        SettingsSnapshot read() throws Exception;
        void write(SettingsSnapshot settings) throws Exception;
        default void synchronize() throws Exception {}
    }

    public interface Effects {
        void builtins();
        void search();
    }

    public enum Result { SUCCESS, READ_FAILED, SAVE_FAILED, APPLY_PENDING }

    private final Storage storage;
    private final Effects effects;
    private volatile SettingsSnapshot accepted = SettingsSnapshot.DEFAULTS;
    private boolean initialized;
    private boolean writable;
    private boolean syncPending;
    private boolean builtinsPending;
    private boolean searchPending;
    private Result result = Result.SUCCESS;

    public SettingsController(Storage storage, Effects effects) {
        this.storage = storage;
        this.effects = effects;
    }

    public SettingsSnapshot snapshot() { return accepted; }
    public boolean initialized() { return initialized; }
    public boolean writable() { return writable; }
    public Result result() { return result; }

    public Result initialize() {
        if (initialized) return reload();
        try {
            accepted = storage.read();
            writable = true;
            result = Result.SUCCESS;
        } catch (Exception error) {
            writable = false;
            result = Result.READ_FAILED;
            Constants.LOG.error("Could not read Collapsible Groups settings", error);
        }
        initialized = true;
        return result;
    }

    public Result reload() {
        if (!initialized) return result;
        try {
            SettingsSnapshot candidate = storage.read();
            writable = true;
            accept(candidate);
            return apply();
        } catch (Exception error) {
            writable = false;
            result = Result.READ_FAILED;
            Constants.LOG.error("Could not reload Collapsible Groups settings", error);
            return result;
        }
    }

    public Result save(SettingsSnapshot candidate) {
        if (!writable) return result = Result.READ_FAILED;
        if (!candidate.equals(accepted)) {
            try {
                storage.write(candidate);
            } catch (Exception error) {
                Constants.LOG.error("Could not save Collapsible Groups settings", error);
                return result = Result.SAVE_FAILED;
            }
            syncPending = true;
            accept(candidate);
        }
        return apply();
    }

    private void accept(SettingsSnapshot candidate) {
        builtinsPending |= candidate.loadDefaultGroups() != accepted.loadDefaultGroups()
            || !candidate.disabledBuiltinCategories().equals(accepted.disabledBuiltinCategories());
        searchPending |= candidate.searchUngroupSmallGroups() != accepted.searchUngroupSmallGroups()
            || candidate.searchUngroupThreshold() != accepted.searchUngroupThreshold();
        accepted = candidate;
    }

    private Result apply() {
        boolean failed = false;
        if (syncPending) {
            try {
                storage.synchronize();
                syncPending = false;
            } catch (Exception error) {
                failed = true;
                Constants.LOG.error("Settings saved; native config synchronization is pending", error);
            }
        }
        try {
            if (builtinsPending) {
                effects.builtins();
                builtinsPending = false;
                searchPending = false;
            } else if (searchPending) {
                effects.search();
                searchPending = false;
            }
        } catch (Exception error) {
            failed = true;
            Constants.LOG.error("Settings saved; display refresh is pending", error);
        }
        return result = failed ? Result.APPLY_PENDING : Result.SUCCESS;
    }
}

