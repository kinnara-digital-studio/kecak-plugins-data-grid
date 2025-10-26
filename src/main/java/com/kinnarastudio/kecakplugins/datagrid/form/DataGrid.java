package com.kinnarastudio.kecakplugins.datagrid.form;

import com.kinnarastudio.commons.Declutter;
import com.kinnarastudio.commons.Try;
import com.kinnarastudio.commons.jsonstream.JSONCollectors;
import com.kinnarastudio.commons.jsonstream.JSONObjectEntry;
import com.kinnarastudio.commons.jsonstream.JSONStream;
import com.kinnarastudio.kecakplugins.datagrid.util.Utilities;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.dao.PackageDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.Grid;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.spring.model.Setting;
import org.joget.commons.util.*;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kecak.apps.exception.ApiException;
import org.kecak.apps.form.model.DataJsonControllerHandler;
import org.kecak.apps.form.service.FormDataUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataGrid extends Element implements FormBuilderPaletteElement, PluginWebSupport, Declutter {
    public final static String PROPS_ENHANCEMENT_STORE_BINDER = "enhancementStoreBinder";
    private final static String LABEL = "Data Grid";
    private final static String CATEGORY = "Kecak";
    private final Map<String, Form> formCache = new HashMap<>();
    protected Map<FormData, FormRowSet> cachedRowSet = new HashMap<>();
    protected Map<String, Map<String, String>> headerMap;
    // Map<Form.Field, Map<Value, Label>>
    protected Map<String, Map<String, String>> optionsMap;

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle() + ". Data Grid with massive functionality improvement built from ground up.";
    }

    @Override
    public String getFormBuilderCategory() {
        return CATEGORY;
    }

    @Override
    public String getFormBuilderIcon() {
        return "/plugin/org.joget.apps.form.lib.TextField/images/textField_icon.gif";
    }

    public int getFormBuilderPosition() {
        return 100;
    }

    @Override
    public String getFormBuilderTemplate() {
        return "<table cellspacing='0'><tbody><tr><th>Header</th><th>Header</th></tr><tr><td>Cell</td><td>Cell</td></tr></tbody></table>";
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getName() {
        return LABEL;
    }

    @Override
    public String getPropertyOptions() {
        Object[] arguments = new Object[]{};
        return AppUtil.readPluginResource(this.getClass().getName(), "/properties/form/DataGrid.json", arguments, true, "messages/DataGrid").replaceAll("\"", "'");
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
    }

    @Override
    public String renderTemplate(FormData formData, @Nonnull Map dataModel) {
        String template = "dataGrid.ftl";
        String decoration = FormUtil.getElementValidatorDecoration(this, formData);
        dataModel.put("decoration", decoration);
        Map<String, Map<String, String>> headers = getHeaderMap();
        dataModel.put("headers", headers);
        String optionsJson = getOptionsJson(headers);
        dataModel.put("optionsJson", optionsJson);
        FormRowSet rows = getRows(formData);
        dataModel.put("rows", rows);
        String buttonLabel;
        if ("true".equals(getPropertyString("readonly"))) {
            buttonLabel = getPropertyString("submit-label-readonly");
            if (buttonLabel.isEmpty()) {
                buttonLabel = ResourceBundleUtil.getMessage("general.method.label.close");
            }
        } else {
            buttonLabel = getPropertyString("submit-label-normal");
            if (buttonLabel.isEmpty()) {
                buttonLabel = ResourceBundleUtil.getMessage("general.method.label.submit");
            }
        }

        {
            Map<String, String> summary = new HashMap<>();
            String jsonOption = calculateSummary(rows, summary);
            dataModel.put("summary", summary);
            dataModel.put("jsonOption", jsonOption);
        }

        dataModel.put("buttonLabel", StringEscapeUtils.escapeHtml4(buttonLabel));
        final String formJson = getSelectedFormJson(formData);
        dataModel.put("json", StringEscapeUtils.escapeHtml4(formJson));
        dataModel.put("defaultValues", this.getDefaultValues(formData));
        dataModel.put("customDecorator", this.getDecorator());
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        dataModel.put("appId", appDef.getAppId());
        dataModel.put("appVersion", appDef.getVersion());
        dataModel.put("className", getClassName());
        String nonceForm = generateNonce(appDef.getAppId(), appDef.getVersion(), formJson);
        dataModel.put("nonceForm", nonceForm);
        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    protected String generateNonce(String appId, long appVersion, String json) {
        return SecurityUtil.generateNonce(new String[]{"EmbedForm", appId, String.valueOf(appVersion), json}, 1);
    }

    @Override
    public FormRowSet formatData(FormData formData) {
        // attempt to load data from store binder, most likely existing data
        FormRowSet rowSet = Optional.ofNullable(getStoreBinder())
                .map(formData::getStoreBinderData)
                .orElseGet(FormRowSet::new);

        if (rowSet.isEmpty()) {
            // get new data from formData, most likely new data
            rowSet = Optional.of(formData)
                    .map(this::getRows)
                    .map(Try.onFunction(this::convertJsonToFormRowSet))
                    .orElseGet(FormRowSet::new);
        }

        rowSet.setMultiRow(true);

        return rowSet;
    }

    public String formatColumn(String name, String recordId, String value) {
        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        return formatColumn(name, getColumnProperty(name), recordId, value, appDefinition.getAppId(), appDefinition.getVersion(), "");
    }

    /**
     * This method will be executed from template file
     *
     * @param name
     * @param header
     * @param recordId
     * @param value
     * @param appId
     * @param appVersion
     * @param contextPath
     * @return
     */
    public String formatColumn(String name, Map<String, String> header, String recordId, String value, String appId, Long appVersion, String contextPath) {
        @Nonnull final String formatType = header.getOrDefault("formatType", "");
        @Nonnull final String format = header.getOrDefault("format", "");
        @Nonnull final String field = String.valueOf(name); // empty indicates summary
        final SetupManager setupManager = (SetupManager) AppUtil.getApplicationContext().getBean("setupManager");

        if (value == null) {
            value = "";
        }

        try {
            String laterToBeEscaped = "";

            if (formatType.equals("text")) {
                laterToBeEscaped = ifEmptyThen(format.replaceAll("\\{\\?}", value), value);
            }

            // Format type : HTML
            else if (formatType.equals("html")) {
                return AppUtil.processHashVariable(ifEmptyThen(format.replaceAll("\\{\\?}", value), value), null, null, null);
            }

            // Format type : DIV TAG (used in mofiz)
            else if (formatType.equals("divTag")) {
                return AppUtil.processHashVariable(ifEmptyThen(format.replaceAll("\\{\\?}", value), value)
                        .replaceAll("^", "<div class='grid-bubble'>")
                        .replaceAll(";", "</div><div class='grid-bubble'>")
                        .replaceAll("$", "</div>"), null, null, null);

            }

            // Format type : LOCALE
            else if (formatType.equals("locale")) {
                Locale locale = Optional.of(format)
                        .map(String::trim)
                        .filter(not(String::isEmpty))
                        .map(this::getLocale)

                        // get from system setting
                        .orElseGet(() -> Optional.of("systemLocale")
                                .map(setupManager::getSettingByProperty)
                                .map(Setting::getValue)
                                .map(String::trim)
                                .filter(not(String::isEmpty))
                                .map(this::getLocale)
                                .orElse(Locale.getDefault()));

                NumberFormat fmt = NumberFormat.getNumberInstance(locale);
                laterToBeEscaped = fmt.format(Double.parseDouble(value));
            }

            // Format Type : DECIMAL
            else if (formatType.equals("decimal") && format != null) {
                value = ifEmptyThen(value, "0");

                int decimal = Integer.parseInt(ifEmptyThen(format, "0"));
                StringBuilder pattern = new StringBuilder(!value.equals("0") ? "#" : "0");
                if (decimal > 0) {
                    pattern.append(".");
                    for (int i = 0; i < decimal; ++i) {
                        pattern.append("0");
                    }
                    Double number = Double.parseDouble(value);
                    DecimalFormat myFormatter = new DecimalFormat(pattern.toString());
                    laterToBeEscaped = myFormatter.format(number);
                }
            }

            // Format Type : DATE
            else if (formatType.equals("date")) {
                String[] dateFormat = format.split("\\|");
                if (dateFormat.length == 2) {
                    SimpleDateFormat f1 = new SimpleDateFormat(dateFormat[0]);
                    SimpleDateFormat f2 = new SimpleDateFormat(dateFormat[1]);
                    Date date = f1.parse(value);
                    laterToBeEscaped = f2.format(date);
                }
            }

            // Format Type : FILE / IMAGE
            else if (formatType.equals("file") || formatType.equals("image")) {
                return Arrays.stream(explodes(value))
                        .map(Try.onFunction(str -> {
                            final File file = FileManager.getFileByPath(str);

                            if (DataGrid.this.isEmpty(recordId) || file != null || DataGrid.this.isEmpty(str)) {
                                return null;
                            }

                            final String formDefId = ifEmptyThen(format, getPropertyFormDefId());
                            final String encodedFileName = URLEncoder.encode(str, "UTF8").replaceAll("\\+", "%20");
                            final String filePath = contextPath + "/web/client/app/" + appId + "/" + appVersion + "/form/download/" + formDefId + "/" + recordId + "/" + encodedFileName;

                            if (formatType.equals("image")) {
                                String imgfilePath = filePath + ".thumb.jpg" + ".";
                                return "<a href='" + filePath + ".' target='_blank' ><img src='" + imgfilePath + "'/></a> ";
                            }

                            // formatType.equals("file")
                            else {
                                return "<a href='" + filePath + ".?attachment=true' target='_blank' >" + str.replaceAll("[^a-zA-Z0-9]+$", "") + "</a> ";
                            }
                        }))
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" "));
            }

            // Format type : URL
            else if (formatType.equals("url")) {
                return Arrays.stream(explodes(value))
                        .map(val -> {
                            final String formId = getPropertyFormDefId();
                            final String href = addUrlParameter(format, "id", val);
                            final String target = "_black";
                            final String label = getValueLabel(formId, field, val);
                            return "<a href='" + href + "' target='" + target + "' >" + label + "</a> ";
                        })
                        .collect(Collectors.joining("&nbsp;"));
            }

            // Format type : OPTIONS
            else if (formatType.equals("options")) {
                final String formDefId;
                final String fieldId;

                final Entry<String, String> formAndField = getFormAndField(header, format);
                formDefId = formAndField.getKey();
                fieldId = formAndField.getValue();

                laterToBeEscaped = getValueLabel(formDefId, fieldId, value);
            }

            // Format type : OPTIONS MULTI-VALUE
            else if (formatType.equalsIgnoreCase("optionvalues")) {
                final String formDefId;
                final String fieldId;

                final Entry<String, String> formAndField = getFormAndField(header, format);
                formDefId = formAndField.getKey();
                fieldId = formAndField.getValue();

                laterToBeEscaped = Arrays.stream(value.split(";"))
                    .map(String::trim)
                    .filter(Predicate.not(String::isEmpty))
                    .map(v -> getValueLabel(formDefId, fieldId, v))
                    .collect(Collectors.joining(";"));
            }

            // Format type : C-INTEGER
            else if (!value.isEmpty() && formatType.equals("cint")) {
                laterToBeEscaped = String.format("%" + format + "d", Integer.parseInt(value));
            }

            // Format type : C-FLOAT
            else if (!value.isEmpty() && formatType.equals("cfloat")) {
                laterToBeEscaped = String.format("%" + format + "f", Double.parseDouble(value));
            }

            // Format type : FORM
            else if (formatType.equals("form")) {
                for (Object o : header.keySet()) {
                    Map.Entry<String, String> e = (Map.Entry<String, String>) o;
                    // TODO
                }
                laterToBeEscaped = value;
            }

            // others
            else {
                laterToBeEscaped = value;
            }

            return AppUtil.processHashVariable(StringEscapeUtils.escapeHtml4(laterToBeEscaped), null, null, null);

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            return value;
        }
    }

    protected Map<String, Map<String, String>> getHeaderMap() {
        if (this.headerMap == null) {
            this.headerMap = new HashMap<>();
            Map<String, String>[] columnProperties = getColumnProperties();
            FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
            int i = 0;
            final Map<String, Form> formCache = new HashMap<>();
            for (Map<String, String> optMap : columnProperties) {
                Object value = getField(optMap);
                if (value == null) continue;

                // inject attachment form
                String format = optMap.get("format");
                if (optMap.get("formatType").equals("form") && !format.isEmpty()) {
                    Form formAttachment = generateForm(format, formCache);
                    FormUtil.setReadOnlyProperty(formAttachment);
                    optMap.put("jsonform_formattachment", formService.generateElementJson(formAttachment));
                }

                this.headerMap.put(String.format("%02d", i), optMap);
                i++;
            }
        }

        return headerMap;
    }

    /**
     * Get rows
     *
     * @param formData
     * @return
     */
    protected FormRowSet getRows(FormData formData) {
        if (!cachedRowSet.containsKey(formData)) {
            String id = getPropertyString(FormUtil.PROPERTY_ID);
            String param = FormUtil.getElementParameterName(this);

            FormRowSet rowSet;
            String json = getPropertyString(FormUtil.PROPERTY_VALUE);
            if (json != null && !json.isEmpty()) {
                try {
                    rowSet = parseFormRowSetFromJson(json);
                } catch (Exception ex) {
                    LogUtil.error(Grid.class.getName(), ex, "Error parsing grid JSON");
                    rowSet = new FormRowSet();
                }
            } else {
                // try to get data from DataJsonController
                if (formData.getRequestParameter(DataJsonControllerHandler.PARAMETER_DATA_JSON_CONTROLLER) != null) {
                    rowSet = Optional.of(formData)
                            .map(fd -> fd.getRequestParameterValues(param))
                            .stream()
                            .flatMap(Arrays::stream)
                            .map(Try.onFunction(JSONObject::new))
                            .map(this::convertJsonToFormRow)
                            .collect(Collectors.toCollection(FormRowSet::new));

                }

                // try to get data as jsonrow_[index], most likely came from web
                else {
                    rowSet = Optional.of(formData)
                            .map(FormData::getRequestParams)
                            .map(Map::entrySet)
                            .stream()
                            .flatMap(Collection::stream)
                            .filter(e -> e.getKey().equals(param) || e.getKey().contains(param + "_jsonrow"))
                            .map(Entry::getValue)
                            .filter(Objects::nonNull)
                            .flatMap(Arrays::stream)
                            .filter(this::isNotEmpty)
                            .map(s -> s.replaceAll("^<textarea[^>]+>", "").replaceAll("</textarea>$", ""))
                            .map(Try.onFunction(JSONObject::new))
                            .map(this::convertJsonToFormRow)
                            .collect(Collectors.toCollection(FormRowSet::new));
                }
            }

            final FormRowSet binderRowSet = formData.getLoadBinderData(this);
            if (!FormUtil.isFormSubmitted(this, formData) && binderRowSet != null) {
                if (!binderRowSet.isMultiRow()) {
                    if (!binderRowSet.isEmpty()) {
                        final FormRow row = binderRowSet.get(0);
                        final String jsonValue = row.getProperty(id);
                        rowSet = this.parseFormRowSetFromJson(jsonValue);
                    }
                } else {
                    rowSet = this.convertFormRowToJson(binderRowSet);
                }

                if (rowSet != null && "true".equals(getPropertyString("enableSorting")) && !"".equals(getPropertyString("sortField"))) {
                    final String sortField = getPropertyString("sortField");

                    rowSet.sort((row1, row2) -> {
                        String number1 = row1.getProperty(sortField);
                        String number2 = row2.getProperty(sortField);
                        if (number1 != null && number2 != null) {
                            try {
                                return Integer.valueOf(number1).compareTo(Integer.valueOf(number2));
                            } catch (Exception e) {
                                // treat as String
                                return number1.compareTo(number2);
                            }
                        }
                        return 0;
                    });
                }
            }
            cachedRowSet.put(formData, rowSet);
        }
        return cachedRowSet.get(formData);
    }

    protected FormRowSet parseFormRowSetFromJson(String json) {
        final FormRowSet rowSet = Optional.ofNullable(json)
                .map(String::trim)
                .map(Try.onFunction(JSONArray::new))
                .map(jsonArray -> JSONStream.of(jsonArray, Try.onBiFunction(JSONArray::getJSONObject)))
                .orElseGet(Stream::empty)
                .map(jsonRow -> JSONStream.of(jsonRow, Try.onBiFunction(JSONObject::getString))
                        .collect(() -> {
                            final FormRow row = new FormRow();
                            row.setProperty("jsonrow", jsonRow.toString());
                            return row;
                        }, (r, e) -> r.setProperty(e.getKey(), e.getValue()), FormRow::putAll))
                .collect(FormRowSet::new, FormRowSet::add, FormRowSet::addAll);

        rowSet.setMultiRow(true);

        return rowSet;
    }

    protected FormRowSet convertFormRowToJson(FormRowSet oriRowSet) {
        final Map<String, Form> formCache = new HashMap<>();

        final FormRowSet rowSet = Optional.ofNullable(oriRowSet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(Try.onFunction(row -> {
                    final JSONObject jsonObject = new JSONObject();
                    final FormRow newRow = new FormRow();

                    for (Map.Entry<Object, Object> entry : row.entrySet()) {
                        String key = entry.getKey().toString();
                        String value = entry.getValue().toString();

                        jsonObject.put(key, value);
                        newRow.setProperty(key, value);

                        // set label for form attachment
                        final JSONObject getFormAttachmentJsonRow = getFormAttachmentJsonRow(key, value, formCache);
                        if (isFormAttachment(key) && getFormAttachmentJsonRow != null) {
                            String formAttachmentKey = key + "_label_formattachment";
                            String formAttachmentKeyJsonRow = key + "_jsonrow_formattachment";
                            String formAttachmentLabel = getFormAttachmentLabel(key, value, formCache);
                            jsonObject.put(formAttachmentKey, formAttachmentLabel);
                            newRow.setProperty(formAttachmentKey, formAttachmentLabel);
                            newRow.setProperty(formAttachmentKeyJsonRow, getFormAttachmentJsonRow.toString());
                        }
                    }

                    newRow.setProperty("jsonrow", jsonObject.toString());
                    return newRow;
                }))
                .collect(FormRowSet::new, FormRowSet::add, FormRowSet::addAll);

        rowSet.setMultiRow(true);
        return rowSet;
    }

    private boolean isFormAttachment(String key) {
        Map<String, String>[] columnProperties = getColumnProperties();
        for (Map<String, String> columnProperty : columnProperties) {
            String fieldName = getField(columnProperty);
            String formatType = String.valueOf(columnProperty.get("formatType"));
            if (key.equals(fieldName) && "form".equals(formatType)) {
                return true;
            }
        }
        return false;
    }

    private FormRowSet getFormRowSet(String formDefId, String primaryKey, Map<String, Form> formCache) {
        // retrieve data from form based on primary key
        Form form = generateForm(formDefId, formCache);
        if (form != null) {
            FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
            FormData formData = new FormData();
            formData.setPrimaryKeyValue(primaryKey);
            formService.executeFormLoadBinders(form, formData);
            return formData.getLoadBinderData(form);
        }

        return null;
    }

    private String getFormAttachmentLabel(String columnKey, String primaryKey, Map<String, Form> formCache) {
        // get grid's form
        Form formGridForm = generateForm(getPropertyFormDefId(), formCache);
        Element formAttachmentElement = searchElementById(formGridForm, columnKey);
        if (formAttachmentElement != null) {
            FormRowSet rowSet = getFormRowSet(formAttachmentElement.getPropertyString("formDefId"), primaryKey, formCache);

            // since we retrieve using primary key, we assume the result is single row
            return Optional.ofNullable(rowSet)
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .findFirst()
                    .map(r -> r.getProperty(formAttachmentElement.getPropertyString("displayField"), primaryKey))
                    .orElse(primaryKey);
        }

        return primaryKey;
    }

    /**
     * Get data in json format
     *
     * @param columnKey
     * @param primaryKey
     * @param formCache
     * @return
     */
    private JSONObject getFormAttachmentJsonRow(String columnKey, String primaryKey, Map<String, Form> formCache) {
        // construct grid's form
        Form formGridForm = generateForm(getPropertyFormDefId(), formCache);

        Element formAttachmentElement = searchElementById(formGridForm, columnKey);
        if (formAttachmentElement != null) {
            FormRowSet rowSet = getFormRowSet(formAttachmentElement.getPropertyString("formDefId"), primaryKey, formCache);

            // since we retrieve using primary key, we assume the result is single row
            if (rowSet != null && rowSet.get(0) != null) {
                JSONObject jsonRow = new JSONObject();
                for (Entry<Object, Object> entry : rowSet.get(0).entrySet()) {
                    try {
                        jsonRow.put(entry.getKey().toString(), entry.getValue().toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                return jsonRow;
            }
        }

        return null;
    }

    private Element searchElementById(Element parent, String idToSearch) {
        Iterator<Element> i = parent.getChildren().iterator();
        while (i.hasNext()) {
            Element child = i.next();
            if (child.getPropertyString(FormUtil.PROPERTY_ID).equals(idToSearch)) {
                return child;
            } else {
                Element grandchild = searchElementById(child, idToSearch);
                if (grandchild != null)
                    return grandchild;
            }
        }

        return null;
    }

    protected FormRowSet convertJsonToFormRowSet(FormRowSet oriRowSet) throws JSONException {
        FormRowSet rowSet = new FormRowSet();
        rowSet.setMultiRow(true);
        int i = 0;
        for (FormRow row : oriRowSet) {
            FormRow newRow = Optional.of("jsonrow")
                    .map(row::get)
                    .map(String::valueOf)
                    .map(s -> s.replaceAll("^<textarea[^>]+>", "").replaceAll("</textarea>$", ""))
                    .map(Try.onFunction(JSONObject::new))
                    .map(this::convertJsonToFormRow)
                    .orElseGet(FormRow::new);

            if (getPropertyString("enableSorting") != null && getPropertyString("enableSorting").equals("true") && getPropertyString("sortField") != null && !getPropertyString("sortField").isEmpty()) {
                newRow.put(getPropertyString("sortField"), Integer.toString(i));
            }
            rowSet.add(newRow);
            ++i;
        }
        return rowSet;
    }

    @Nonnull
    protected FormRow convertJsonToFormRow(JSONObject jsonObject) {
        FormRow newRow = new FormRow();
        newRow.setProperty("jsonrow", jsonObject.toString());

        JSONStream.of(jsonObject, Try.onBiFunction(JSONObject::getString))
                .forEach(Try.onConsumer(entry -> {
                    String fieldName = entry.getKey();
                    if (fieldName.equals(FormUtil.PROPERTY_TEMP_FILE_PATH)) {
                        Optional.of(entry)
                                .map(JSONObjectEntry::getValue)
                                .map(Try.onFunction(JSONObject::new))
                                .map(j -> JSONStream.of(j, Try.onBiFunction(JSONObject::getString)))
                                .orElseGet(Stream::empty)
                                .forEach(Try.onConsumer(e -> {
                                    String[] value = Optional.of(e)
                                            .map(Map.Entry::getValue)
                                            .map(Try.onFunction(JSONArray::new))
                                            .map(s -> JSONStream.of(s, Try.onBiFunction(JSONArray::getString)))
                                            .orElseGet(Stream::empty).toArray(String[]::new);
                                    newRow.putTempFilePath(e.getKey(), value);
                                }));

                    } else {
                        String value = entry.getValue();
                        newRow.setProperty(fieldName, value);
                    }
                }));

        return newRow;
    }

    protected Form generateForm(String formDefId) {
        return generateForm(formDefId, null);
    }

    /**
     * Construct form from formId
     *
     * @param formDefId
     * @param formCache
     * @return
     */
    protected Form generateForm(String formDefId, Map<String, Form> formCache) {
        return generateForm(formDefId, null, formCache);
    }

    /**
     * Construct form from formId
     *
     * @param formDefId
     * @param processId
     * @param formCache
     * @return
     */
    @Nullable
    protected Form generateForm(String formDefId, String processId, Map<String, Form> formCache) {
        if (formDefId == null || formDefId.isEmpty())
            return null;

        // check in cache
        if (formCache != null && formCache.containsKey(formDefId))
            return formCache.get(formDefId);

        // proceed without cache
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        Form form;
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        PackageDefinitionDao packageDefinitionDao = (PackageDefinitionDao) AppUtil.getApplicationContext().getBean("packageDefinitionDao");

        AppDefinition appDef = Optional.ofNullable(processId)
                .map(workflowManager::getRunningProcessById)
                .filter(p -> p.getPackageId() != null && p.getVersion() != null)
                .map(Try.onFunction(process -> {
                    String packageId = process.getPackageId();
                    Long packageVersion = Long.parseLong(process.getVersion());
                    return packageDefinitionDao.loadPackageDefinition(packageId, packageVersion);
                }))
                .map(PackageDefinition::getAppDefinition)
                .orElseGet(AppUtil::getCurrentAppDefinition);

        if (appDef != null) {
            FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null) {
                FormData formData = new FormData();
                String json = formDef.getJson();
                if (processId != null && !processId.isEmpty()) {
                    formData.setProcessId(processId);
                    WorkflowManager wm = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
                    WorkflowAssignment wfAssignment = wm.getAssignmentByProcess(processId);
                    if (wfAssignment != null)
                        json = AppUtil.processHashVariable(json, wfAssignment, "json", null);
                }
                form = (Form) formService.createElementFromJson(json);

                // put in cache if possible
                if (formCache != null)
                    formCache.put(formDefId, form);

                return form;
            }
        }
        return null;
    }

    /**
     * @param parentFormData
     * @return
     */
    protected String getSelectedFormJson(FormData parentFormData) {
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        String processId = parentFormData.getProcessId();
        if (processId == null || processId.equals("")) {
            processId = parentFormData.getPrimaryKeyValue();
        }
        Form form = generateForm(getPropertyFormDefId(), processId, null);
        if (form != null) {
            JsonFormBinder storeBinder = new JsonFormBinder();
            form.setStoreBinder(storeBinder);

            JsonFormBinder loadBinder = new JsonFormBinder();
            form.setLoadBinder(loadBinder);

            try {
                // to be sent to JsonFormBinder
                form.setProperty(JsonFormBinder.PROPERTY_GRID_ELEMENT, FormUtil.generateElementJson(this));
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, e.getMessage());
            }

            boolean readonly = "true".equalsIgnoreCase(getPropertyString("readonly"));
            boolean readonlyLabel = "true".equalsIgnoreCase(getPropertyString("readonlyLabel"));
            if (readonly || readonlyLabel) {
                FormUtil.setReadOnlyProperty(form, readonly, readonlyLabel);
            }
            form.setProperty(PROPS_ENHANCEMENT_STORE_BINDER, getProperty(PROPS_ENHANCEMENT_STORE_BINDER));

            return formService.generateElementJson(form);
        }

        return "";
    }

    /**
     * @param formData
     * @return
     */
    @Override
    public Boolean selfValidate(FormData formData) {
        FormRowSet rowSet = getRows(formData);
        String id = FormUtil.getElementParameterName(this);

        boolean valid = Optional.ofNullable(getPropertyString("validateMinRow"))
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (Exception e) {
                        LogUtil.error(getClassName(), e, e.getMessage());
                        return 0;
                    }
                })
                .map(i -> rowSet.size() >= i)
                .orElse(true);

        valid = Optional.ofNullable(getPropertyString("validateMaxRow"))
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (Exception e) {
                        LogUtil.error(getClassName(), e, e.getMessage());
                        return 0;
                    }
                })
                .map(i -> rowSet.size() <= i)
                .orElse(valid);

        if (!valid) {
            String errorMsg = getPropertyString("errorMessage");
            formData.addFormError(id, errorMsg);
        }
        return valid;
    }

    /**
     * @return
     */
    protected String getDecorator() {
        String decorator = "";
        try {
            boolean decorate = Optional.ofNullable(getPropertyString("validateMinRow"))
                    .filter(s -> !s.isEmpty())
                    .map(Try.onFunction(Integer::parseInt))
                    .map(i -> i > 0)
                    .orElse(false);

            if (decorate) {
                decorator = "*";
            }
        } catch (Exception e) {
            // empty catch block
        }
        return decorator;
    }

    /**
     * @param headers
     */
    protected Map<String, Map<String, String>> loadOptionsMap(Map<String, Map<String, String>> headers) {
        // check if options map is not already assigned
        if (optionsMap != null) {
            return this.optionsMap;
        }

        optionsMap = new HashMap<>();

        final AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        final FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        final FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");

        final Map<String, String> forms = new HashMap<>();

        for (String key : headers.keySet()) {
            final Map<String, String> header = headers.get(key);
            final String formatType = header.getOrDefault("formatType", "");
            final String format = header.getOrDefault("format", "");

            final String formDefId;
            final String fieldId;

            // URL
            if ("url".equalsIgnoreCase(formatType)) {
                formDefId = getPropertyFormDefId();
                fieldId = header.get("value");
            }

            // OPTIONS
            else if ("options".equals(formatType)) {
                final Entry<String, String> formAndField = getFormAndField(header, format);
                formDefId = formAndField.getKey();
                fieldId = formAndField.getValue();
            }

            // OPTIONS MULTI-VALUE
            else if ("optionvalues".equals(formatType)) {
                final Entry<String, String> formAndField = getFormAndField(header, format);
                formDefId = formAndField.getKey();
                fieldId = formAndField.getValue();
            }

            // no supported format
            else continue;

            // start collecting options

            final String optionsKey = formDefId + "." + fieldId;

            // skip if options is already collected for current optionsKey
            if (optionsMap.containsKey(optionsKey)) continue;

            if (!forms.containsKey(formDefId)) {
                final FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
                final String json = formDef.getJson();
                final String formJson = AppUtil.processHashVariable(json, null, "json", null)
                        .replaceAll("'\\{}'", "{}");

                forms.put(formDefId, formJson);
            }

            final String formJson = forms.get(formDefId);
            final Element element = FormUtil.findAndParseElement(formJson, fieldId);

            // skip if current element is not found in form
            if (element == null) continue;

            element.setProperty("controlField", "");

            final FormData formData = formService.executeFormOptionsBinders(element, new FormData());
            final Collection<FormRow> optionMap = FormUtil.getElementPropertyOptionsMap(element, formData);

            final Map<String, String> options = optionMap.stream()
                    .collect(Collectors.toMap(row -> row.getProperty("value"), row -> row.getProperty("label")));

            if (options.isEmpty()) {
                LogUtil.warn(getClassName(), "Options binder in form [" + formDefId + "] field [" + fieldId + "] is not found");
            }

            this.optionsMap.put(optionsKey, options);
        }

        return optionsMap;
    }

    protected String getOptionsJson(Map<String, Map<String, String>> headers) {
        final Map<String, Map<String, String>> optionsMap = loadOptionsMap(headers);

        final JSONObject jsonObject = Optional.of(optionsMap)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .collect(JSONCollectors.toJSONObject(
                        e -> e.getKey().split("\\.")[0],
                        Entry::getValue));

        return jsonObject.toString();
    }

    protected JSONObject getJsonProperties(FormData formData) {
        final JSONObject jsonProperties = new JSONObject();
        try {
            jsonProperties.put("readonly", FormUtil.isReadonly(this, formData));
            jsonProperties.put("disabledAdd", "true".equalsIgnoreCase(getPropertyString("disabledAdd")));
        } catch (JSONException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }
        return jsonProperties;
    }

    /**
     * @return
     */
    @Override
    public Collection<String> getDynamicFieldNames() {
        ArrayList<String> fieldNames = new ArrayList<String>();
        if (this.getStoreBinder() == null) {
            fieldNames.add(getPropertyString(FormUtil.PROPERTY_ID));
        }
        return fieldNames;
    }

    /**
     * @param rows
     * @param summary
     * @return
     */
    private String calculateSummary(FormRowSet rows, Map<String, String> summary) {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        JSONObject jsonOption = new JSONObject();

        Map<String, String>[] columnProperties = getColumnProperties();
        for (Map<String, String> columnProperty : columnProperties) {
            try {
                jsonOption.put(getField(columnProperty), columnProperty.get("summary"));
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
            if (columnProperty.get("summary") != null) {
                String fieldId = String.valueOf(columnProperty.get("value"));
                String operation = String.valueOf(columnProperty.get("summary"));
                if ("sum".equals(operation)) {
                    BigDecimal sumValue = new BigDecimal(0);
                    for (FormRow row : rows) {
                        BigDecimal value = getValue(row, fieldId);
                        sumValue = sumValue.add(value);
                    }

                    summary.put(fieldId, formatColumn(fieldId, columnProperty, null, sumValue.toString(), appDef.getAppId(), appDef.getVersion(), ""));

                } else if ("avg".equals(operation)) {
                    BigDecimal sumValue = new BigDecimal(0);
                    for (FormRow row : rows) {
                        BigDecimal value = getValue(row, fieldId);
                        sumValue = sumValue.add(value);
                    }
                    summary.put(fieldId, !rows.isEmpty() ?
                            sumValue.divide(new BigDecimal(rows.size())).toString() : "0");
                } else if ("count".equals(operation)) {
                    summary.put(fieldId, String.format("%d", rows.size()));
                } else if ("max".equals(operation)) {
                    BigDecimal max = new BigDecimal(Integer.MIN_VALUE);
                    for (FormRow row : rows) {
                        BigDecimal value = getValue(row, fieldId);
                        if (value.compareTo(max) > 0) max = value;
                    }
                    summary.put(fieldId, max.toString());
                } else if ("min".equals(operation)) {
                    BigDecimal min = new BigDecimal(Integer.MAX_VALUE);
                    for (FormRow row : rows) {
                        BigDecimal value = getValue(row, fieldId);
                        if (value.compareTo(min) < 0) min = value;
                    }
                    summary.put(fieldId, min.toString());
                }
            }
        }

        return jsonOption.toString();
    }

    /**
     * @param formData
     * @return
     */
    private String getDefaultValues(FormData formData) {
        if (getProperty("defaultValues") == null)
            return null;

        WorkflowManager wm = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        WorkflowAssignment wfAssignment = wm.getAssignment(formData.getActivityId());
        JSONObject result = new JSONObject();
        try {
            for (Object o : (Object[]) getProperty("defaultValues")) {
                Map<String, String> row = (Map<String, String>) o;
                String value = AppUtil.processHashVariable(row.get("value"), wfAssignment, null, null);

                // remove untranslated hash variables
                result.put(row.get("fieldName"), value.replaceAll("#[a-z,A-Z,_]+[a-z,A-Z,_,0-9]*\\.[^#]+#", ""));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result.toString();
    }

    /**
     * @param row
     * @param fieldId
     * @return
     */
    private BigDecimal getValue(FormRow row, String fieldId) {
        BigDecimal value;
        try {
            value = new BigDecimal(row == null || row.getProperty(fieldId) == null ? "0" : row.getProperty(fieldId).replaceAll(",", ""));
        } catch (NumberFormatException e) {
            value = new BigDecimal(0);
        }
        return value;
    }

    /**
     * @param locale
     * @return
     */
    private Locale getLocale(@Nonnull String locale) {
        return getLocale(locale, "en_US");
    }

    /**
     * @param locale
     * @param defaultValue
     * @return
     */
    private Locale getLocale(@Nonnull String locale, String defaultValue) {
        String value = !locale.isEmpty() ? locale
                : defaultValue == null ? "en_ "
                : defaultValue;
        String[] split = value.split("_");
        return value.isEmpty() ? new Locale("en", "") : new Locale(split[0].trim(), split.length > 1 ? split[1].trim() : "");
    }

    /**
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String formId = getRequiredParameter(request, "formId");
            Form form = Optional.ofNullable(generateForm(formId))
                    .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, String.format("Form [%s] is not available", formId)));

            String sectionId = getOptionalParameter(request, "sectionId");
            Section section = findElement(sectionId, form, Section.class);

            Element parent = section != null ? section : form;
            String formGridId = getRequiredParameter(request, "formGridId");
            DataGrid dataGrid = Optional.ofNullable(findElement(formGridId, parent, DataGrid.class))
                    .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, String.format("Form Grid Element [%s] is not available in section [%s] form [%s]", formGridId, sectionId, formId)));

            String method = request.getMethod();
            if ("GET".equalsIgnoreCase(method)) {
                String action = getRequiredParameter(request, "action");
                if ("format".equalsIgnoreCase(action)) {
                    String columnName = getRequiredParameter(request, "columnName");
                    String value = getRequiredParameter(request, "value");
                    String id = getRequiredParameter(request, "id");
                    Map<String, String> columnProperty = getColumnProperty(columnName);
                    AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
                    String formattedValue = dataGrid.formatColumn(columnName, columnProperty, id, value, appDefinition.getAppId(), appDefinition.getVersion(), "");

                    final JSONObject responseBody = new JSONObject();
                    try {
                        responseBody.put("formattedValue", formattedValue);
                        response.getWriter().write(responseBody.toString());
                    } catch (JSONException e) {
                        throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e);
                    }
                } else {
                    throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, "Action is not recognized");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                String contentType = request.getContentType();
                if (contentType.contains("multipart/form-data")) {
                    postMultipartFormData(request, response, dataGrid);
                } else {
                    postApplicationJson(request, response, dataGrid);
                }
            } else {
                throw new ApiException(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method [" + method + "] is not allowed");
            }

        } catch (ApiException e) {
            response.sendError(e.getErrorCode(), e.getMessage());
        }
    }

    /**
     * @param request
     * @param response
     * @param dataGrid
     * @throws ApiException
     */
    protected void postMultipartFormData(HttpServletRequest request, HttpServletResponse response, DataGrid dataGrid) throws ApiException {
        try {
            ApplicationContext applicationContext = AppUtil.getApplicationContext();
            WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");

            String primaryKey = getOptionalParameter(request, "primaryKey", UuidGenerator.getInstance().getUuid());
            String activityId = getOptionalParameter(request, "activityId");

            final FormData formData = new FormData();
            formData.setPrimaryKeyValue(primaryKey);

            // set workflow assignment
            Optional.of(activityId)
                    .map(workflowManager::getAssignment)
                    .ifPresent(a -> {
                        formData.setActivityId(a.getActivityId());
                        formData.setProcessId(a.getProcessId());
                    });

            Form form = dataGrid.getAttachmentForm();

            final Map<String, String[]> tempFilePath = new HashMap<>();

            FormRow row = Utilities.elementStream(form, formData)
                    .filter(not(e -> e instanceof FormContainer))
                    .collect(FormRow::new, Try.onBiConsumer((r, e) -> {
                        final String elementId = e.getPropertyString("id");
                        final String parameterName = FormUtil.getElementParameterName(e);

                        if (e instanceof FileDownloadSecurity) {
                            List<String> originalFilenames = new ArrayList<>();
                            List<String> filePathList = new ArrayList<>();

                            Optional.of(elementId)
                                    .map(Try.onFunction(FileStore::getFiles))
                                    .map(Arrays::stream)
                                    .orElseGet(Stream::empty)
                                    .forEach(file -> {
                                        final String filePath = FileManager.storeFile(file);
                                        filePathList.add(filePath);
                                        originalFilenames.add(file.getOriginalFilename());

                                    });

                            if (!originalFilenames.isEmpty()) {
                                r.setProperty(elementId, String.join(";", originalFilenames));
                            }

                            if (!filePathList.isEmpty()) {
                                String[] filePaths = filePathList.toArray(new String[0]);
                                tempFilePath.put(elementId, filePaths);
                                formData.addRequestParameterValues(parameterName, filePaths);
                            }

                        } else {
                            Optional.of(elementId)
                                    .filter(not(String::isEmpty))
                                    .map(request::getParameter)
                                    .ifPresent(s -> {
                                        r.setProperty(elementId, s);
                                        formData.addRequestParameterValues(parameterName, new String[]{s});
                                    });
                        }
                    }), FormRow::putAll);

            if (!tempFilePath.isEmpty()) {
                row.setProperty(FormUtil.PROPERTY_TEMP_FILE_PATH, new JSONObject(tempFilePath).toString());
            }

            final FormData validatedFormData = validateFormData(form, formData);
            final Map<String, String> formErrors = validatedFormData.getFormErrors();

            final JSONObject responseBody = new JSONObject();
            if (formErrors != null && !formErrors.isEmpty()) {
                responseBody.put("validation_error", new JSONObject(formErrors));
            } else {
                FormRow enhancementRow = Utilities.executeOnFormSubmitEnhancement(dataGrid, row, validatedFormData);

                final JSONObject jsonData = new JSONObject(enhancementRow);

                Optional.of(enhancementRow)
                        .map(r -> r.getProperty(FormUtil.PROPERTY_TEMP_FILE_PATH))
                        .filter(not(String::isEmpty))
                        .map(Try.onFunction(JSONObject::new))
                        .map(json -> JSONStream.of(json, Try.onBiFunction(JSONObject::getJSONArray)))
                        .orElseGet(Stream::empty)
                        .forEach(Try.onConsumer(e -> jsonData.putOpt(e.getKey(), e.getValue())));

                // remove temp file path data
                enhancementRow.remove(FormUtil.PROPERTY_TEMP_FILE_PATH);

                jsonData.putOpt("_" + FormUtil.PROPERTY_ID, enhancementRow.getId());

                jsonData.putOpt("_" + FormUtil.PROPERTY_ID, enhancementRow.getId());
                jsonData.putOpt("_" + FormUtil.PROPERTY_DATE_CREATED, enhancementRow.getDateCreated());
                jsonData.putOpt("_" + FormUtil.PROPERTY_CREATED_BY, enhancementRow.getCreatedBy());
                jsonData.putOpt("_" + FormUtil.PROPERTY_DATE_MODIFIED, enhancementRow.getDateModified());
                jsonData.putOpt("_" + FormUtil.PROPERTY_MODIFIED_BY, enhancementRow.getModifiedBy());

                String digest = getOptionalParameter(request, "digest");
                String currentDigest = getDigest(jsonData);
                responseBody.put("digest", currentDigest);
                if (!digest.equalsIgnoreCase(currentDigest)) {
                    responseBody.put("data", jsonData);
                }
            }

            response.getWriter().write(responseBody.toString());

        } catch (IOException | JSONException e) {
            throw new ApiException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * @param request
     * @param response
     * @param dataGrid
     * @throws ApiException
     */
    protected void postApplicationJson(final HttpServletRequest request, final HttpServletResponse response, final DataGrid dataGrid) throws ApiException {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        WorkflowManager workflowManager = (WorkflowManager) applicationContext.getBean("workflowManager");

        String primaryKey = getOptionalParameter(request, "primaryKey", UuidGenerator.getInstance().getUuid());
        String activityId = getOptionalParameter(request, "activityId");

        JSONObject requestBody = getRequestBody(request);

        Form attachmentForm = dataGrid.getAttachmentForm();

        final FormData formData = new FormData();
        formData.setPrimaryKeyValue(primaryKey);

        // set workflow assignment
        Optional.of(activityId)
                .map(workflowManager::getAssignment)
                .ifPresent(a -> {
                    formData.setActivityId(a.getActivityId());
                    formData.setProcessId(a.getProcessId());
                });

        FormRow row = processRequestBody(attachmentForm, formData, requestBody);

        final FormData validatedFormData = validateFormData(attachmentForm, formData);
        final Map<String, String> formErrors = validatedFormData.getFormErrors();
        final JSONObject responseBody = new JSONObject();
        try {
            if (formErrors == null || formErrors.isEmpty()) {
                FormRow enhancementRow = Utilities.executeOnFormSubmitEnhancement(dataGrid, row, validatedFormData);
                JSONObject jsonData = new JSONObject(enhancementRow);
                jsonData.putOpt("_" + FormUtil.PROPERTY_ID, enhancementRow.getId());

                jsonData.putOpt("_" + FormUtil.PROPERTY_ID, enhancementRow.getId());
                jsonData.putOpt("_" + FormUtil.PROPERTY_DATE_CREATED, enhancementRow.getDateCreated());
                jsonData.putOpt("_" + FormUtil.PROPERTY_CREATED_BY, enhancementRow.getCreatedBy());
                jsonData.putOpt("_" + FormUtil.PROPERTY_DATE_MODIFIED, enhancementRow.getDateModified());
                jsonData.putOpt("_" + FormUtil.PROPERTY_MODIFIED_BY, enhancementRow.getModifiedBy());

                String digest = getOptionalParameter(request, "digest");
                String currentDigest = getDigest(jsonData);
                responseBody.put("digest", currentDigest);
                if (!digest.equalsIgnoreCase(currentDigest)) {
                    responseBody.put("data", jsonData);
                }
            } else {
                responseBody.put("validation_error", new JSONObject(formErrors));
            }
        } catch (JSONException e) {
            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }

        try {
            response.getWriter().write(responseBody.toString());
        } catch (IOException e) {
            throw new ApiException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * @param form
     * @param formData
     * @param requestBody
     * @return
     */
    @Nonnull
    protected FormRow processRequestBody(@Nonnull final Form form, @Nonnull final FormData formData, JSONObject requestBody) {
        final FormRow row = new FormRow();

        Utilities.elementStream(form, formData)
                .filter(not(e -> e instanceof FormContainer))
                .forEach(Try.onConsumer(element -> {
                    String key = element.getPropertyString("id");
                    String parameterName = FormUtil.getElementParameterName(element);
                    String value = requestBody.getString(key);
                    formData.addRequestParameterValues(parameterName, new String[]{value});
                    row.put(key, value);
                }));

        return row;
    }


    /**
     * Validate form data
     *
     * @param form
     * @param formData
     * @return
     */
    @Nonnull
    private FormData validateFormData(Form form, FormData formData) {
        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        FormService formService = (FormService) applicationContext.getBean("formService");

        FormData updatedFormData = FormUtil.executeElementFormatDataForValidation(form, formData);
        FormData validatedFormData = formService.validateFormData(form, updatedFormData);

        return validatedFormData;
    }

    /**
     * Extract body from request
     *
     * @param request
     * @return
     * @throws ApiException
     */
    private JSONObject getRequestBody(HttpServletRequest request) throws ApiException {
        try {
            InputStream inputStream = request.getInputStream();
            try (BufferedReader bf = new BufferedReader(new InputStreamReader(inputStream))) {
                return new JSONObject(bf.lines().collect(Collectors.joining()));
            } catch (JSONException e) {
                throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            }
        } catch (IOException e) {
            throw new ApiException(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }

    }

    @Nonnull
    private String getRequiredParameter(HttpServletRequest request, String parameterName) throws ApiException {
        return Optional.ofNullable(request.getParameter(parameterName))
                .filter(s -> !s.isEmpty())
                .orElseThrow(() -> new ApiException(HttpServletResponse.SC_BAD_REQUEST, String.format("Parameter [%s] is not supplied", parameterName)));
    }

    @Nonnull
    private String getOptionalParameter(HttpServletRequest request, @Nonnull String parameterName) {
        return getOptionalParameter(request, parameterName, "");
    }

    @Nonnull
    private String getOptionalParameter(HttpServletRequest request, @Nonnull String parameterName, @Nonnull String defaultValue) {
        return Optional.ofNullable(request.getParameter(parameterName))
                .filter(s -> !s.isEmpty())
                .orElse(defaultValue);
    }

    @Nullable
    private <E extends Element> E findElement(@Nonnull String id, Element parentElement, Class<E> clazz) {
        return (E) Optional.of(id)
                .filter(s -> !s.isEmpty())
                .map(s -> FormUtil.findElement(s, parentElement, null))
                .filter(clazz::isInstance)
                .orElse(null);
    }

    /**
     * Calculate digest (version if I may call) but will omit "elementUniqueKey"
     *
     * @param json JSON object
     * @return digest value
     */
    private String getDigest(JSONObject json) {
        return DigestUtils.sha256Hex(json == null || json.toString() == null ? "" : json.toString());
    }

    @Nonnull
    public Map<String, String>[] getColumnProperties() {
        return Optional.of("options")
                .map(this::getProperty)
                .map(o -> (FormRowSet) o)
                .stream()
                .flatMap(Collection::stream)
                .map(r -> r.keySet().stream()
                        .map(String::valueOf)
                        .collect(Collectors.toMap(Objects::toString, r::getProperty)))
                .toArray((IntFunction<Map<String, String>[]>) Map[]::new);
    }

    @Nullable
    public String getField(Map<String, String> map) {
        return map.get("value");
    }

    protected String getColumnType(Map<String, String> map) {
        return map.get("formatType");
    }

    protected String getColumnFormat(Map<String, String> map) {
        return map.get("format");
    }

    protected String getColumnLabel(Map<String, String> map) {
        return map.get("label");
    }

    protected String getPropertyFormDefId() {
        return getPropertyString("formDefId");
    }

    protected JSONArray getJsonRowData(FormRowSet rowSet) {
        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        final Map<String, String>[] columnProperties = getColumnProperties();
        return Optional.ofNullable(rowSet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .map(Try.onFunction(row -> {
                    final JSONObject jsonRow = new JSONObject(row);
                    return Arrays.stream(columnProperties)
                            .collect(JSONCollectors.toJSONObject(JSONObject::new, m -> m.get("value"), property -> {
                                String key = property.get("value");
                                String value = row.getProperty(key);
                                return formatColumn(key, property, row.getId(), value, appDefinition.getAppId(), appDefinition.getVersion(), "");
                            }, Try.onFunction(json -> {
                                json.put("id", row.getId());

                                json.remove("jsonrow");
                                json.put("jsonrow", Utilities.getJsonrowString(this, jsonRow.toString()));
                                return json;
                            })));
                }))
                .collect(JSONCollectors.toJSONArray());
    }

    private int getColumnIndex(Map<String, String>[] columnProperties, String fieldName) {
        for (int i = 0, size = columnProperties.length; i < size; i++) {
            if (fieldName.equals(getField(columnProperties[i]))) {
                return i;
            }
        }
        return -1;
    }

    private JSONArray getJsonDataTableColumns(FormData formData) {
        Map<String, String>[] columnProperties = getColumnProperties();
        final JSONArray columnDefs = Optional.of(columnProperties)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(Try.onFunction(m -> {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("data", getField(m));
                    jsonObject.put("name", getField(m));
                    jsonObject.put("type", getColumnType(m));
                    jsonObject.put("format", getColumnFormat(m));
                    jsonObject.put("title", getColumnLabel(m));
                    jsonObject.put("className", "grid-cell dt-body-" + getColumnAlignment(m));
                    return jsonObject;
                }))
                .filter(Objects::nonNull)
                .collect(JSONCollectors.toJSONArray());

        try {
            JSONObject jsonColumnAction = new JSONObject();
            jsonColumnAction.put("name", "_action");
            jsonColumnAction.put("sortable", false);
            jsonColumnAction.put("width", "20px");
            if (!FormUtil.isReadonly(this, formData)) {
                jsonColumnAction.put("defaultContent", "<i class='dataTable-edit ace-icon fa fa-edit bigger-130 green'></i>&nbsp;<i class='dataTable-delete ace-icon fa fa-trash bigger-130 red'></i>");
            } else {
                jsonColumnAction.put("defaultContent", "<i class='dataTable-view ace-icon fa fa-eye bigger-130 blue'></i>");
            }
            columnDefs.put(jsonColumnAction);
        } catch (JSONException ignored) {
        }

        try {
            JSONObject jsonRow = new JSONObject();
            jsonRow.put("name", "jsonrow");
            jsonRow.put("data", "jsonrow");
            jsonRow.put("className", "grid-column-jsonrow");
            jsonRow.put("sortable", false);
            columnDefs.put(jsonRow);
        } catch (JSONException ignored) {
        }
        return columnDefs;
    }

    public String getFooter() {
        return Optional.ofNullable(getColumnProperties())
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(m -> "<th name='" + getField(m) + "' class='grid-summary summary-" + m.getOrDefault("summary", "none") + "' data-summary='" + m.getOrDefault("formatType", "none") + "' data-formatType='" + m.getOrDefault("formatType", "") + "' data-format='" + m.getOrDefault("format", "") + "' style='text-align:right'></th>")
                .collect(Collectors.joining("", "<tr>", "</tr>"));
    }

    public String getColumnAlignment(Map<String, String> columnProperties) {
        return columnProperties.getOrDefault("alignment", "left");
    }

    public Map<String, String> getColumnProperty(String columnName) {
        return Optional.of(getColumnProperties())
                .stream()
                .flatMap(Arrays::stream)
                .filter(m -> columnName.equalsIgnoreCase(m.getOrDefault("value", "")))
                .findFirst()
                .orElseGet(HashMap::new);
    }

    public Form getAttachmentForm() {
        return generateForm(getPropertyString("formDefId"), formCache);
    }


    @Override
    public String[] handleMultipartDataRequest(@Nonnull String[] values, @Nonnull Element gridElement, @Nonnull FormData formData) {
        final Form attachmentForm = ((DataGrid) gridElement).getAttachmentForm();

        return Arrays.stream(values)
                .map(Try.onFunction(JSONArray::new))

                // stream grid's line content
                .flatMap(j -> JSONStream.of(j, Try.onBiFunction(JSONArray::getJSONObject)))
                .map(Try.onFunction(j -> reconstructRowObject(attachmentForm, formData, j)))
                .map(JSONObject::toString)
                .map(s -> AppUtil.processHashVariable(s, formData.getAssignment(), null, null))
                .toArray(String[]::new);
    }

    @Override
    public String[] handleJsonDataRequest(@Nullable Object value, @Nonnull Element element, @Nonnull FormData formData) throws JSONException {
        if (value == null) return new String[0];

        final Form attachmentForm = ((DataGrid) element).getAttachmentForm();

        final JSONArray json;
        if (value instanceof JSONArray) {
            json = (JSONArray) value;
        } else {
            json = new JSONArray(String.valueOf(value));
        }

        return JSONStream.of(json, Try.onBiFunction(JSONArray::getJSONObject))
                .map(Try.onFunction(j -> reconstructRowObject(attachmentForm, formData, j)))
                .map(JSONObject::toString)
                .map(s -> AppUtil.processHashVariable(s, formData.getAssignment(), null, null))
                .toArray(String[]::new);
    }

    @Override
    public Object handleElementValueResponse(@Nonnull Element element, FormData formData) {
        final boolean asOptions = formData.getRequestParameter(DataJsonControllerHandler.PARAMETER_AS_OPTIONS) != null;

        if (element.getLoadBinder() == null) {
            // data is stored in field as JSONArray
            final String elementId = element.getPropertyString(FormUtil.PROPERTY_ID);
            return Optional.ofNullable(formData.getLoadBinderData(element))
                    .stream()
                    .flatMap(Collection::stream)
                    .findFirst()
                    .map(r -> r.getProperty(elementId))
                    .map(Try.onFunction(JSONArray::new))
                    .stream()
                    .flatMap(j -> JSONStream.of(j, Try.onBiFunction(JSONArray::getJSONObject)))
                    .map(j -> JSONStream.of(j, Try.onBiFunction(JSONObject::getString))
                            .collect(Collectors.toMap(JSONObjectEntry::getKey, JSONObjectEntry::getValue, (replace, accept) -> accept, FormRow::new)))
                    .map(r -> collectGridElement((DataGrid) element, r, asOptions))
                    .collect(JSONCollectors.toJSONArray());
        } else {
            // data is loaded from load binder
            return Optional.of(element)
                    .map(formData::getLoadBinderData)
                    .stream()
                    .flatMap(Collection::stream)
                    .map(r -> collectGridElement((DataGrid) element, r, asOptions))
                    .collect(JSONCollectors.toJSONArray());
        }

    }

    protected JSONObject collectElement(@Nonnull final Element element, @Nonnull final FormRow row) {
        Objects.requireNonNull(element);

        final JSONObject jsonObject = row.entrySet().stream()
                .collect(JSONCollectors.toJSONObject(e -> e.getKey().toString(), Map.Entry::getValue));

        FormDataUtil.collectRowMetaData(row, jsonObject);

        return jsonObject;
    }

    protected JSONObject collectGridElement(@Nonnull DataGrid dataGridElement, @Nonnull FormRow row, boolean asOptions) {
        final AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        final Map<String, String>[] columnProperties = dataGridElement.getColumnProperties();

        final Form form = dataGridElement.generateForm(dataGridElement.getPropertyFormDefId());
        final FormData formData = new FormData() {{
            Optional.of(row).map(FormRow::getId).ifPresent(this::setPrimaryKeyValue);
        }};

        final String primaryKey = Optional.of(row).map(FormRow::getId).orElse("");

        final JSONObject jsonObject = FormDataUtil.elementStream(form, formData)
                .filter(e -> !(e instanceof FormContainer))
                .collect(JSONCollectors.toJSONObject(e -> e.getPropertyString(FormUtil.PROPERTY_ID), Try.onFunction(e -> {
                    final String columnName = e.getPropertyString(FormUtil.PROPERTY_ID);
                    final Optional<Map<String, String>> optProps = Optional.of(columnProperties)
                            .stream()
                            .flatMap(Arrays::stream)
                            .filter(m -> columnName.equals(dataGridElement.getField(m)))
                            .findAny();

                    if(optProps.isEmpty()) {
                        return row.containsKey(columnName) ? String.valueOf(row.get(columnName)) : null;
                    }

                    final Map<String, String> props = optProps.get();

                    final String columnType = optProps
                            .map(m -> m.getOrDefault("formatType", ""))
                            .orElse("");

                    return Optional.of(columnName)
                            .filter(s -> !s.isEmpty())
                            .map(row::get)
                            .map(String::valueOf)
                            .map(propertyValue -> {
                                if (asOptions && "options".equals(columnType)) {
                                    return Arrays.stream(propertyValue.split(";"))
                                            .filter(Predicate.not(String::isEmpty))
                                            .map(Try.onFunction(val -> {
                                                String formattedValue = dataGridElement.formatColumn(columnName, props, primaryKey, val, appDefinition.getAppId(), appDefinition.getVersion(), "");
                                                JSONObject json = new JSONObject();
                                                json.put(FormUtil.PROPERTY_VALUE, val);
                                                json.put(FormUtil.PROPERTY_LABEL, formattedValue);
                                                return json;
                                            }, (String failover, JSONException ignored) -> failover))
                                            .collect(JSONCollectors.toJSONArray());
                                } else {
                                    return dataGridElement.formatColumn(columnName, props, primaryKey, propertyValue, appDefinition.getAppId(), appDefinition.getVersion(), "");
                                }
                            })
                            .orElse("");
                })));

//        final JSONObject jsonObject = Optional.of(columnProperties)
//                .stream()
//                .flatMap(Arrays::stream)
//                .collect(JSONCollectors.toJSONObject(dataGridElement::getField, props -> {
//                    final String columnName = Optional.of(props)
//                            .map(dataGridElement::getField)
//                            .orElse("");
//                    final String columnType = Optional.of(props)
//                            .map(m -> m.getOrDefault("formatType", ""))
//                            .orElse("");
//
//                    return Optional.of(columnName)
//                            .filter(s -> !s.isEmpty())
//                            .map(row::getProperty)
//                            .map(s -> {
//                                if (asOptions && "options".equals(columnType)) {
//                                    return Optional.of(";")
//                                            .map(s::split)
//                                            .stream()
//                                            .flatMap(Arrays::stream)
//                                            .filter(Objects::nonNull)
//                                            .map(Try.onFunction(value -> {
//                                                String formattedValue = dataGridElement.formatColumn(columnName, props, primaryKey, value, appDefinition.getAppId(), appDefinition.getVersion(), "");
//                                                JSONObject json = new JSONObject();
//                                                json.put(FormUtil.PROPERTY_VALUE, value);
//                                                json.put(FormUtil.PROPERTY_LABEL, formattedValue);
//                                                return json;
//                                            }, (String failover, JSONException ignored) -> failover))
//                                            .collect(JSONCollectors.toJSONArray());
//                                } else {
//                                    return dataGridElement.formatColumn(columnName, props, primaryKey, s, appDefinition.getAppId(), appDefinition.getVersion(), "");
//                                }
//                            })
//                            .orElse(null);
//                }));

        FormDataUtil.collectRowMetaData(row, jsonObject);

        return jsonObject;
    }


    /**
     * @param gridRow
     * @param attachmentForm
     * @param formData
     * @return
     */
    protected JSONObject constructTempFilePath(JSONObject gridRow, Form attachmentForm, FormData formData) {
        return JSONStream.of(gridRow, Try.onBiFunction(JSONObject::getString))
                .filter(e -> {
                    String elementId = e.getKey();
                    Element element = FormUtil.findElement(elementId, attachmentForm, formData, true);
                    return element instanceof FileDownloadSecurity;
                })
                .collect(JSONCollectors.toJSONObject(Map.Entry::getKey, e -> {
                    String data = e.getValue();
                    Matcher m = FormDataUtil.DATA_PATTERN.matcher(data);

                    String tempFilePath;

                    // as data uri
                    if (m.find()) {
                        String contentType = m.group("mime");
                        String extension = contentType.split("/")[1];
                        String fileName = FormDataUtil.getFileName(m.group("properties"), extension);
                        String base64 = m.group("data");

                        // store in app_tempupload
                        MultipartFile multipartFile = FormDataUtil.decodeFile(fileName, contentType, base64.trim());
                        return FileManager.storeFile(multipartFile);
                    } else {
                        return e.getValue();
                    }
                }));
    }

    /**
     * @param gridRow
     * @param attachmentForm
     * @param formData
     * @return
     */
    protected JSONObject reconstructJsonResultWithAttributeTempFilePath(JSONObject gridRow, Form attachmentForm, FormData formData) {
        return JSONStream.of(gridRow, Try.onBiFunction(JSONObject::getString))
                .collect(JSONCollectors.toJSONObject(Map.Entry::getKey, Try.onFunction(e -> {
                    String elementId = e.getKey();
                    Element element = FormUtil.findElement(elementId, attachmentForm, formData, true);
                    if (element instanceof FileDownloadSecurity) {
                        JSONArray jsonValue = new JSONArray(e.getValue());
                        return JSONStream.of(jsonValue, Try.onBiFunction(JSONArray::getString))
                                .map(s -> s.replaceFirst("[\\w-]+/", ""))
                                .collect(Collectors.joining(";"));
                    }
                    return e.getValue();
                })));
    }

    /**
     * @param attachmentForm
     * @param formData
     * @return
     * @throws JSONException
     */
    protected JSONObject reconstructRowObject(Form attachmentForm, FormData formData, JSONObject json) throws JSONException {
        final JSONObject source = FormDataUtil.elementStream(attachmentForm, formData)
                .collect(JSONCollectors.toJSONObject(e -> e.getPropertyString("id"), Try.onFunction(e -> {
                    final String elementId = e.getPropertyString("id");
                    final Object value = json.opt(elementId);
                    return Arrays.stream(e.handleJsonDataRequest(value, e, formData))
                            .findFirst()
                            .orElse("");
                })));

        JSONObject jsonTempFilePath = constructTempFilePath(source, attachmentForm, formData);
        JSONObject result = reconstructJsonResultWithAttributeTempFilePath(source, attachmentForm, formData);

        if (jsonTempFilePath.length() > 0) {
            result.put(FormUtil.PROPERTY_TEMP_FILE_PATH, jsonTempFilePath.toString());
        }

        if (!result.has(FormUtil.PROPERTY_ID)) {
            // put default ID
            result.put(FormUtil.PROPERTY_ID, UUID.randomUUID().toString());
        }

        return result;
    }

    protected String[] explodes(String source) {
        return Optional.ofNullable(source)
                .map(s -> s.split(";"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(String::trim)
                .filter(this::isNotEmpty)
                .toArray(String[]::new);
    }

    protected String addUrlParameter(String url, String key, String value) {
        return url + (url.contains("?") ? "&" : "?") + String.format("%s=%s", key, value);
    }

    protected String getValueLabel(String formDefId, String fieldId, String value) {
        final Map<String, Map<String, String>> optionsMap = loadOptionsMap(getHeaderMap());
        return Optional.of(optionsMap)
                .map(m -> m.get(String.join(".", formDefId, fieldId)))
                .map(m -> m.get(value))
                .orElse(value);
    }

    protected Entry<String, String> getFormAndField(Map<String, String> header, String format) {
        final String field = header.getOrDefault("value", "");
        final String formDefId;
        final String fieldId;

        if (isEmpty(format)) {
            formDefId = getPropertyFormDefId();
            fieldId = field;
        } else if (format.contains(".")) {
            final String[] split = format.split("\\.", 2);
            formDefId = split[0];
            fieldId = split[1];
        } else {
            formDefId = format;
            fieldId = field;
        }

        return new SimpleImmutableEntry<>(formDefId, fieldId);
    }

    protected final <T> T coalesce(T... values) {
        if (values == null) return null;

        for (T value : values) if (value != null) return value;

        return null;
    }
}