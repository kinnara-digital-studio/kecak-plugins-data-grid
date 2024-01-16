package com.kinnarastudio.kecakplugins.datagrid;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.lib.HiddenField;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kecak.apps.exception.ApiException;
import org.springframework.beans.BeansException;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DataGridBinder
        extends FormBinder
        implements FormLoadBinder,
        FormStoreBinder,
        FormLoadMultiRowElementBinder,
        FormStoreMultiRowElementBinder,
        FormDataDeletableBinder,
        PluginWebSupport {

    private final Map<String, Form> formCache = new HashMap<>();

    private String tableName = null;

    private final static String LABEL = "Data Grid Binder";

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getPropertyOptions() {
        Object[] arguments = {getClassName()};
        return AppUtil.readPluginResource(getClass().getName(),
                "/properties/DataGridBinder.json", arguments, true);
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        FormRowSet rows = new FormRowSet();
        Form form = this.getSelectedForm();
        if (form != null && primaryKey != null) {
            try {
                final FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
                final String propertyName = this.getFormPropertyName(form, this.getPropertyString("foreignKey"));
                final StringBuilder condition = new StringBuilder(propertyName != null && !propertyName.isEmpty() ? " WHERE " + propertyName + " = ?" : "");
//                Object[] paramsArray = new Object[]{primaryKey};
                List<Object> paramsArray = new ArrayList<>();
                paramsArray.add(primaryKey);

                if (getProperty("extraCondition") != null) {
                    for (Object o : (Object[]) getProperty("extraCondition")) {
                        Map<String, String> row = (Map<String, String>) o;
                        if ((row.get("key") != null || !row.get("key").isEmpty())) {
                            condition.append(" AND ").append(this.getFormPropertyName(form, row.get("key"))).append(" = ? ");
                        }

                        if ((row.get("value") != null || !row.get("value").isEmpty())) {
                            paramsArray.add(AppUtil.processHashVariable(row.get("value").trim(), null, null, null, appDef));
                        }
                    }
                }
                rows = formDataDao.find(form, condition.toString(), paramsArray.toArray(), "dateCreated", false, null, null);
            } catch (BeansException e) {
                LogUtil.error(DataGridBinder.class.getName(), e, e.getMessage());
            }
        }
        rows.setMultiRow(true);
        return rows;
    }

    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        if (rows == null) {
            return null;
        }

        FormDataDao formDataDao = (FormDataDao) FormUtil.getApplicationContext().getBean("formDataDao");
        AppService appService = (AppService) FormUtil.getApplicationContext().getBean("appService");
        Form form = this.getSelectedForm();
        if (form != null) {
            Form parentForm = FormUtil.findRootForm(element);
            String primaryKeyValue = parentForm.getPrimaryKeyValue(formData);
            FormRowSet originalRowSet = this.load(element, primaryKeyValue, formData);
            try {
                if (originalRowSet != null && !originalRowSet.isEmpty()) {
                    List<String> ids = new ArrayList<>();
                    for (FormRow r : originalRowSet) {
                        if (rows.contains(r)) {
                            continue;
                        }
                        ids.add(r.getId());
                    }
                    if (ids.size() > 0) {
                        formDataDao.delete(form, ids.toArray(new String[0]));
                    }
                }

                for (FormRow row : rows) {
                    row.put(this.getPropertyString("foreignKey"), primaryKeyValue);
                }

                rows = appService.storeFormData(form, rows, null);
            } catch (Exception e) {
                LogUtil.error(this.getClass().getName(), e, e.getMessage());
            }
        }
        return rows;
    }

    protected Form getSelectedForm() {
        Form form = null;
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        String formDefId = this.getPropertyString("formDefId");
        if (formDefId != null) {
            String formJson;
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null && (formJson = formDef.getJson()) != null) {
                form = (Form) formService.createElementFromJson(formJson);
            }
        }
        return form;
    }

    protected String getFormPropertyName(Form form, String propertyName) {
        if (propertyName != null && !propertyName.isEmpty() && (((FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao")).getFormDefinitionColumnNames(form.getPropertyString("tableName"))).contains(propertyName) && !"id".equals(propertyName)) {
            propertyName = "customProperties." + propertyName;
        }
        return propertyName;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String formDefId = getRequiredParameter(request, "formDefId");
            Form form = Optional.of(formDefId)
                    .map(this::getForm)
                    .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Unknown form [" + formDefId + "]"));

            JSONArray jsonArray = new JSONArray();
            getChildren(form, e -> e instanceof HiddenField, element -> {
                String elementId = element.getPropertyString("id");
                String elementName = Optional.ofNullable(element.getPropertyString("label"))
                        .filter(s -> !s.isEmpty())
                        .orElse(elementId);
                try {
                    JSONObject jsonElement = new JSONObject();
                    jsonElement.put("value", elementId);
                    jsonElement.put("label", elementName);
                    jsonArray.put(jsonElement);
                } catch (JSONException e) {
                    LogUtil.error(getClassName(), e, e.getMessage());
                }
            });

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(jsonArray.toString());

        } catch (ApiException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            response.sendError(e.getErrorCode(), e.getMessage());
        }
    }

    private String getRequiredParameter(HttpServletRequest request, String parameterName) throws ApiException {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Required parameter [" + parameterName + " is not supplied]"));
    }

    private void getChildren(Element parent, Predicate<Element> filter, Consumer<Element> consumeChild) {
        if (parent == null)
            return;

        for (Element child : parent.getChildren()) {
            if (filter.test(child)) {
                consumeChild.accept(child);
            }

            getChildren(child, filter, consumeChild);
        }
    }

    @Nullable
    private Form getForm(String formDefId) {
        return Optional.ofNullable(formCache.get(formDefId))
                .orElseGet(() -> {
                    AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
                    AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                    Form form = appService.viewDataForm(appDefinition.getAppId(), appDefinition.getVersion().toString(), formDefId, null, null, null, null, null, null);
                    if (form != null) {
                        formCache.put(formDefId, form);
                    }
                    return form;
                });
    }

    @Override
    public String getFormId() {
        return getPropertyString("formDefId");
    }

    @Override
    @Nullable
    public String getTableName() {
        if (tableName == null) {
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            String formDefId = getFormId();
            tableName = appService.getFormTableName(appDef, formDefId);
        }
        return tableName;
    }
}
