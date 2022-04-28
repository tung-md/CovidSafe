package com.android.covidsafe.ui.main.home.vaccination;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.android.covidsafe.repository.ProfileRepository;
import com.android.covidsafe.repository.VaccinationRepository;
import com.android.covidsafe.utilities.AbsentLiveData;
import com.android.covidsafe.vo.Profile;
import com.android.covidsafe.vo.Resource;
import com.android.covidsafe.vo.Vaccination;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;

public class VaccinationViewModel extends ViewModel {

    private final MutableLiveData<String> query = new MutableLiveData<>();

    private final LiveData<Resource<List<Vaccination>>> results;

    private final NextPageHandler nextPageHandler;

    private final LiveData<Resource<Profile>> profileResource;

    @Inject
    VaccinationViewModel(VaccinationRepository vaccinationRepository, ProfileRepository profileRepository) {
        profileResource = profileRepository.loadProfile();
        nextPageHandler = new NextPageHandler(vaccinationRepository);
        results = Transformations.switchMap(query, query -> {
            if (query == null || query.trim().length() == 0) {
                return AbsentLiveData.create();
            } else {
                return vaccinationRepository.getAll(query);
            }
        });
    }

    @VisibleForTesting
    public LiveData<Resource<Profile>> getProfileResource() {
        return profileResource;
    }

    @VisibleForTesting
    public LiveData<Resource<List<Vaccination>>> getResults() {
        return results;
    }

    public void setQuery(@NonNull String originalInput) {
        String input = originalInput.toLowerCase(Locale.getDefault()).trim();
        if (Objects.equals(input, query.getValue())) {
            return;
        }
        nextPageHandler.reset();
        query.setValue(input);
    }

    @VisibleForTesting
    public LiveData<LoadMoreState> getLoadMoreStatus() {
        return nextPageHandler.getLoadMoreState();
    }

    @VisibleForTesting
    public void loadNextPage() {
        String value = query.getValue();
        if (value == null || value.trim().length() == 0) {
            return;
        }
        nextPageHandler.queryNextPage(value);
    }

    void refresh() {
        if (query.getValue() != null) {
            query.setValue(query.getValue());
        }
    }

    static class LoadMoreState {
        private final boolean running;
        private final String errorMessage;
        private boolean handledError = false;

        LoadMoreState(boolean running, String errorMessage) {
            this.running = running;
            this.errorMessage = errorMessage;
        }

        boolean isRunning() {
            return running;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        String getErrorMessageIfNotHandled() {
            if (handledError) {
                return null;
            }
            handledError = true;
            return errorMessage;
        }
    }

    @VisibleForTesting
    static class NextPageHandler implements Observer<Resource<Boolean>> {
        private final MutableLiveData<LoadMoreState> loadMoreState = new MutableLiveData<>();
        private final VaccinationRepository repository;
        @VisibleForTesting
        boolean hasMore;
        @Nullable
        private LiveData<Resource<Boolean>> nextPageLiveData;
        private String query;

        @VisibleForTesting
        NextPageHandler(VaccinationRepository repository) {
            this.repository = repository;
            reset();
        }

        void queryNextPage(String query) {
            if (Objects.equals(this.query, query)) {
                return;
            }
            unregister();
            this.query = query;
            nextPageLiveData = repository.getNextPage(query);
            loadMoreState.setValue(new LoadMoreState(true, null));
            //noinspection ConstantConditions
            nextPageLiveData.observeForever(this);
        }

        @Override
        public void onChanged(@Nullable Resource<Boolean> result) {
            if (result == null) {
                reset();
            } else {
                switch (result.status) {
                    case SUCCESS:
                        hasMore = Boolean.TRUE.equals(result.data);
                        unregister();
                        loadMoreState.setValue(new LoadMoreState(false, null));
                        break;
                    case ERROR:
                        hasMore = true;
                        unregister();
                        loadMoreState.setValue(new LoadMoreState(false,
                                result.message));
                        break;
                }
            }
        }

        private void unregister() {
            if (nextPageLiveData != null) {
                nextPageLiveData.removeObserver(this);
                nextPageLiveData = null;
                if (hasMore) {
                    query = null;
                }
            }
        }

        private void reset() {
            unregister();
            hasMore = true;
            loadMoreState.setValue(new LoadMoreState(false, null));
        }

        MutableLiveData<LoadMoreState> getLoadMoreState() {
            return loadMoreState;
        }
    }
}
