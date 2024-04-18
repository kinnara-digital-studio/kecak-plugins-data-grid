package com.kinnarastudio.kecakplugins.datagrid;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.HiddenField;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.plugin.base.PluginManager;

import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author aristo
 *
 * TODO : Prepare a special type of element for foreign key.
 */
public class ForeignKeyElement extends HiddenField {

    @Override
    public String getFormBuilderCategory() {
        return "Kecak";
    }

    @Override
    public String getName() {
        return getLabel();
    }

    @Override
    public String getFormBuilderTemplate() {

        return "<label class='label'>" + getName() + "</label>";
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        final String template = "dataGridFkField.ftl";
        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "Data Grid FK";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/DataGridForeignKeyElement.json", null, true, "/messages/DataGrid");
    }
}
