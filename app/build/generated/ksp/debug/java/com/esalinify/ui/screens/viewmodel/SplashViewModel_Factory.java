package com.esalinify.ui.screens.viewmodel;

import com.esalinify.data.PreferencesManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class SplashViewModel_Factory implements Factory<SplashViewModel> {
  private final Provider<PreferencesManager> preferencesManagerProvider;

  public SplashViewModel_Factory(Provider<PreferencesManager> preferencesManagerProvider) {
    this.preferencesManagerProvider = preferencesManagerProvider;
  }

  @Override
  public SplashViewModel get() {
    return newInstance(preferencesManagerProvider.get());
  }

  public static SplashViewModel_Factory create(
      Provider<PreferencesManager> preferencesManagerProvider) {
    return new SplashViewModel_Factory(preferencesManagerProvider);
  }

  public static SplashViewModel newInstance(PreferencesManager preferencesManager) {
    return new SplashViewModel(preferencesManager);
  }
}
