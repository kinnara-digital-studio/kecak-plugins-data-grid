package com.kinnarastudio.kecakplugins.datagrid.util;

import com.kinnarastudio.kecakplugins.datagrid.form.DataGrid;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.plugin.base.PluginManager;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Utilities {

    @Nonnull
    public static FormRow executeOnFormSubmitEnhancement(DataGrid dataGrid, @Nonnull FormRow originalRow, FormData validatedFormData) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        PluginManager pluginManager = (PluginManager) applicationContext.getBean("pluginManager");

        final FormRowSet rowSet = new FormRowSet();
        rowSet.add(originalRow);

        Form form = dataGrid.getAttachmentForm();
        Map<String, Object> propStoreBinderEnhancement = (Map<String, Object>) dataGrid.getProperty(DataGrid.PROPS_ENHANCEMENT_STORE_BINDER);
        FormStoreBinder enhancementStoreBinder = pluginManager.getPlugin(propStoreBinderEnhancement);
        return Optional.of(rowSet)
                .map(r -> Utilities.executeOnFormSubmitEnhancement(form, enhancementStoreBinder, r, validatedFormData))
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .orElse(originalRow);
    }

    public static FormRowSet executeOnFormSubmitEnhancement(Element itemForm, FormStoreBinder enhancementStoreBinder, @Nonnull FormRowSet originalRows, FormData validatedFormData) {
        return Optional.ofNullable(enhancementStoreBinder)
                .map(b -> b.store(itemForm, originalRows, validatedFormData))
                .orElse(originalRows);
    }

    public static String getJsonrowString(final DataGrid gridElement, final JSONObject json) {
        return Optional.ofNullable(gridElement)
                .map(FormUtil::getElementParameterName)
                .map(parameterName -> "<textarea class='jsonFormBinder' name='" + parameterName + "_jsonrow'>" + json.toString() + "</textarea>")
                .orElse("");
    }


    @Nonnull
    public static Stream<Element> elementStream(@Nonnull Element element, FormData formData) {
        if (!element.isAuthorize(formData)) {
            return Stream.empty();
        }

        Stream<Element> stream = Stream.of(element);
        for (Element child : element.getChildren()) {
            stream = Stream.concat(stream, elementStream(child, formData));
        }
        return stream;
    }
}
