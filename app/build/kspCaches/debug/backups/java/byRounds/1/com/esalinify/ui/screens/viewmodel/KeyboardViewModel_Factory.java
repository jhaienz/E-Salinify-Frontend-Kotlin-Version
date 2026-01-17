package com.esalinify.ui.screens.viewmodel;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class KeyboardViewModel_Factory implements Factory<KeyboardViewModel> {
  @Override
  public KeyboardViewModel get() {
    return newInstance();
  }

  public static KeyboardViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static KeyboardViewModel newInstance() {
    return new KeyboardViewModel();
  }

  private static final class InstanceHolder {
    private static final KeyboardViewModel_Factory INSTANCE = new KeyboardViewModel_Factory();
  }
}
