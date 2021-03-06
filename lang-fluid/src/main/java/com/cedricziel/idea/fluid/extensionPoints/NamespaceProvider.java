package com.cedricziel.idea.fluid.extensionPoints;

import com.cedricziel.idea.fluid.tagMode.FluidNamespace;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface NamespaceProvider {
    ExtensionPointName<NamespaceProvider> EP_NAME = ExtensionPointName.create("com.cedricziel.idea.fluid.provider.implicitNamespace");

    @NotNull Collection<FluidNamespace> provideForElement(@NotNull PsiElement element);
}
