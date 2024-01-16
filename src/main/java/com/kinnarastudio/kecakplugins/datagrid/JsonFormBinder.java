package com.kinnarastudio.kecakplugins.datagrid;

import com.kinnarastudio.commons.Declutter;
import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONObjectEntry;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 */
public class JsonFormBinder extends FormBinder
        implements FormLoadElementBinder, FormStoreElementBinder, Declutter {

    public final static String PROPERTY_GRID_ELEMENT = "gridElement";

    private final static String LABEL = "Json Form Binder";

    /**
     * Binder will be executed when submitting the attachment form.
     * Rows will be converted to json
     *
     * @param root
     * @param submittedRows assume the FormRowSet is not multirow
     * @param formData
     * @return
     */
    @Override
    public FormRowSet store(Element root, final FormRowSet submittedRows, FormData formData) {
        final FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        final Form form = (Form) root;
        if (submittedRows.isMultiRow()) {
            LogUtil.warn(getClassName(), "Only first row of multirow records will be processed");
        }

        // enhancement
        final PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        final Map<String, Object> enhancementStoreBinderProps = (Map<String, Object>) form.getProperty(DataGrid.PROPS_ENHANCEMENT_STORE_BINDER);
        final FormStoreBinder enhancementStoreBinder = pluginManager.getPlugin(enhancementStoreBinderProps);
        final Optional<FormRow> optRow = Utilities.executeOnFormSubmitEnhancement(form, enhancementStoreBinder, submittedRows, formData).stream().findFirst();
        if (!optRow.isPresent()) {
            return null;
        }

        final FormRow row = optRow.get();

        Optional.of(row)
                .map(FormRow::getId)
                .map(s -> formDataDao.load(form, s))
                .map(FormRow::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .forEach(dbEntry -> {
                    final String id = dbEntry.getKey().toString();
                    final String value = dbEntry.getValue().toString();

                    final Optional<Element> optElement = Optional.of(id)
                            .map(s -> FormUtil.findElement(s, form, formData))
                            .filter(e -> FormUtil.isReadonly(e, formData));

                    optElement.ifPresent(e -> row.setProperty(id, value));
                });

        final Optional<DataGrid> optFormGrid = optGridElement(form);
        if (!optFormGrid.isPresent()) {
            return null;
        }

        final DataGrid grid = optFormGrid.get();

        final JSONObject json = new JSONObject(row);
        extractTempFilePath(row).ifPresent(Try.onConsumer(s -> json.put(FormUtil.PROPERTY_TEMP_FILE_PATH, s)));

        row.put("jsonrow", Utilities.getJsonrowString(grid, json.toString()));

        // set default values
        Arrays.stream(grid.getColumnProperties())
                .map(grid::getField)
                .filter(s -> !row.containsKey(s))
                .forEach(s -> row.put(s, ""));

        // apply formatting
        Arrays.stream(grid.getColumnProperties())
                .forEach(m -> {
                    final String field = grid.getField(m);
                    final String value = row.getProperty(field);
                    final String rowId = row.getId();
                    final String formattedValue = grid.formatColumn(field, m, rowId, value, appDefinition.getAppId(), appDefinition.getVersion(), "");
                    row.setProperty(field, formattedValue);
                });

        final FormRowSet rowSet = new FormRowSet();
        rowSet.add(row);

        return rowSet;
    }

    protected Optional<DataGrid> optGridElement(Form form) {
        return Optional.of(JsonFormBinder.PROPERTY_GRID_ELEMENT)
                .map(form::getPropertyString)
                .map(Try.onFunction(FormUtil::parseElementFromJson))
                .map(e -> (DataGrid) e);
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();

        FormRowSet rowSet = new FormRowSet();
        Optional.of("_jsonFormData")
                .map(request::getParameter)
                .filter(this::isNotEmpty)
                .map(Try.onFunction(JSONObject::new))
                .map(json -> JSONStream.of(json, Try.onBiFunction(JSONObject::get))
                        .collect(Collectors.toMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue, (oldValue, newValue) -> newValue, FormRow::new)))
                .ifPresent(row -> {
                    if (row.containsKey(FormUtil.PROPERTY_TEMP_FILE_PATH)) {
                        try {
                            final JSONObject jsonTempFilePathMap = new JSONObject(row.getProperty(FormUtil.PROPERTY_TEMP_FILE_PATH));
                            JSONStream.of(jsonTempFilePathMap, Try.onBiFunction(JSONObject::getJSONArray))
                                    .forEach(e -> {
                                        final Set<String> tempFilePathSet = JSONStream.of(e.getValue(), Try.onBiFunction(JSONArray::optString)).collect(Collectors.toSet());
                                        final Set<String> filenameSet = Optional.of(e.getKey())
                                                .map(row::getProperty)
                                                .map(s -> s.split(";"))
                                                .map(Arrays::stream)
                                                .orElseGet(Stream::empty)
                                                .filter(s -> !anyMatch(s, tempFilePathSet))
                                                .collect(Collectors.toSet());

                                        row.put(e.getKey(), Stream.concat(tempFilePathSet.stream(), filenameSet.stream()).collect(Collectors.joining(";")));
                                    });
                        } catch (JSONException ex) {
                            LogUtil.error(getClassName(), ex, ex.getMessage());
                        }
                    }

                    rowSet.add(row);
                });

        return rowSet;
    }

    protected boolean anyMatch(String filename, Set<String> tempFilePathSet) {
        return tempFilePathSet.stream().map(s -> s.replaceAll("^[a-z0-9-]+/", "")).anyMatch(filename::equals);
    }

    protected Optional<String> extractTempFilePath(FormRow row) {
        return Optional.of(row)
                .map(FormRow::getTempFilePathMap)
                .map(Try.onFunction(JSONObject::new))
                .map(JSONObject::toString);
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return null;
    }

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
}
