
<div class="form-cell" ${elementMetaData!}>
<#assign formGridId = "formgrid_" + elementParamName! + "_" + element.properties.elementUniqueKey >
<#if !(request.getAttribute(className)??) >
    <link href="${request.contextPath}/js/boxy/stylesheets/boxy.css" rel="stylesheet" type="text/css" />
    <link href="${request.contextPath}/plugin/${className}/css/gridPaging.css" rel="stylesheet" type="text/css" />
    <script type="text/javascript" src="${request.contextPath}/plugin/${className}/js/sprintf.min.js"></script>
    <script type="text/javascript" src="${request.contextPath}/plugin/${className}/js/jquery.kecakdatagrid.js"></script>
    <script type="text/javascript" src="${request.contextPath}/plugin/${className}/js/date.min.js"></script>
    <script type="text/javascript" src="${request.contextPath}/plugin/${className}/js/jquery.gridPaging.min.js"></script>

    <style type="text/css">
        .grid table {
            width: 100%;
        }
        .grid th, .grid td {
            border: solid 1px silver;
            margin: 0px;
        }
        .grid-cell-options {
            width: 10px;
        }
        .grid-row-template {
            display: none;
        }
        .grid-cell input:focus {
            background: #efefef;
            border: 1px solid #a1a1a1;
        }
        .grid-action-edit,
        .grid-action-delete,
        .grid-action-moveup,
        .grid-action-movedown,
        .grid-action-add{
            display:inline-block;
            height:16px;
            width:16px;
        }
        .grid-action-delete{
            background: url(${request.contextPath}/images/v3/property_editor/delete.png) no-repeat;
        }
        .grid-action-moveup{
            display:none;
            background: url(${request.contextPath}/images/v3/property_editor/up.png) no-repeat;
        }
        .grid-action-movedown{
            display:none;
            background: url(${request.contextPath}/images/v3/property_editor/down.png) no-repeat;
        }
        .grid-action-add{
            margin-top:3px;
            background: url(${request.contextPath}/images/v3/property_editor/add.png) no-repeat;
        }
        .grid-action-edit{
            background: url(${request.contextPath}/plugin/${className}/images/edit.png) no-repeat;
        }
        .grid-action-moveup.disabled{
            background: url(${request.contextPath}/images/v3/property_editor/up_d.png) no-repeat;
        }
        .grid-action-movedown.disabled{
            background: url(${request.contextPath}/images/v3/property_editor/down_d.png) no-repeat;
        }
        .grid-action-edit span,
        .grid-action-delete span,
        .grid-action-moveup span,
        .grid-action-movedown span,
        .grid-action-add span{
            display:none;
        }
        .grid.enableSorting a.grid-action-moveup,
        .grid.enableSorting a.grid-action-movedown{
            display:inline-block;
        }
        .grid.readonly.enableSorting a.grid-action-moveup,
        .grid.readonly.enableSorting a.grid-action-movedown,
        .grid.readonly a.grid-action-delete,
        .grid.readonly a.grid-action-add,
        .grid.disabledAdd a.grid-action-add,
        .grid.disabledDelete a.grid-action-delete{
            display:none;
        }
        .grid.readonly a.grid-action-edit{
            display:inline-block;
            background: url(${request.contextPath}/plugin/${className}/images/view.png) no-repeat;
        }
        .grid .grid-action-header, .grid .grid-action-cell {
            border: 0 none;
        }

        .grid-bubble {
            background: #f4f1f1;
            padding: 8px 15px;
            display: inline-block;
            border-radius: 10px;
            text-align: center;
            font-size: 13px !important;
            margin: 1px 5px;
        }
    </style>
</#if>

<script type="text/javascript">

    $(document).ready(function() {
        $("#${formGridId}").kecakdatagrid({options : ${optionsJson!}});
        $("#${formGridId}").kecakdatagrid("initPopupDialog",
            {
                contextPath:'${request.contextPath}',
                title:'Add Entry',
                javascriptEnhancementOnFormLoad : ${element.properties.javascriptEnhancementOnFormLoad!'function(){}'}
            }
        );
    	// temporarily commented, bcoz calculation issue
        //$("#${formGridId}").gridPaging({<#if element.properties.enableSorting! == 'true'>dataSorting : true</#if>});
    });

    function ${formGridId}_add(args){
        $("#${formGridId}").kecakdatagrid("addRow", args);
    }

    function ${formGridId}_edit(args){
        $("#${formGridId}").kecakdatagrid("editRow", args);
    }
    
    function ${formGridId}_display_form_attachment(args){
        $("#${formGridId}").kecakdatagrid("editRow", args);
    }
</script>

    <label class="label">${element.properties.label!?html} <span class="form-cell-validator">${decoration}${customDecorator}</span><#if error??> <span class="form-error-message">${error}</span></#if></label>
    <div class="form-clear"></div>
    <div id="${formGridId}" name="${elementParamName!}" class="grid form-element <#if element.properties.readonly! == 'true'>readonly</#if> <#if element.properties.enableSorting! == 'true'>enableSorting</#if> <#if element.properties.disabledAdd! == 'true'>disabledAdd</#if> <#if element.properties.disabledDelete! == 'true'>disabledDelete</#if>">
        <input type="hidden" disabled="disabled" id="formUrl" value="${request.contextPath}/web/app/${appId}/${appVersion}/form/embed?_submitButtonLabel=${buttonLabel!?html}">
        <input type="hidden" disabled="disabled" id="attachmentFormUrl" value="${request.contextPath}/web/app/${appId}/${appVersion}/form/embed?_submitButtonLabel=Close">
        <input type="hidden" disabled="disabled" id="json" value="${json!}">
        <input type="hidden" disabled="disabled" id="appId" value="${appId!}">
        <input type="hidden" disabled="disabled" id="appVersion" value="${appVersion!}">
        <input type="hidden" disabled="disabled" id="contextPath" value="${request.contextPath}">
        <input type="hidden" disabled="disabled" id="height" value="${element.properties.height!}">
        <input type="hidden" disabled="disabled" id="width" value="${element.properties.width!}">
        <input type="hidden" disabled="disabled" id="uniqueKey" value="${element.properties.uniqueKey!}">
        <input type="hidden" disabled="disabled" id="validateMaxRow" value="${element.properties.validateMaxRow!}">
        <input type="hidden" disabled="disabled" id="deleteMessage" value="${element.properties.deleteMessage!?html}">
        <input type="hidden" disabled="disabled" id="nonce" value="${nonceForm!?html}">
        <input type="hidden" disabled="disabled" id="defaultValues" value="${defaultValues!?html}">
        <table cellspacing="0" style="width:100%;"  class="tablesaw tablesaw-stack" data-tablesaw-mode="stack">
            <#if element.properties.hideHeader?? && element.properties.hideHeader! != "true">
                <thead>
                    <tr>
                        <#if element.properties.showRowNumber?? && element.properties.showRowNumber! != "">
                            <th></th>
                        </#if>
                        <#list headers?keys?sort as key>
                            <#assign width = "">
                            <#if headers[key]['width']?? && headers[key]['width'] != "">
                                <#assign width = "width:" + headers[key]['width'] >
                            </#if>
                            <#if headers[key]['formatType'] == 'form'>
                                <th id="${elementParamName!}_${headers[key]['value']?html}" style="${width}">
                                    <!-- form -->
                                    ${headers[key]['label']!?html}
                                    <textarea style="display:none" class="jsonform_formattachment">${headers[key]['jsonform_formattachment']!?html}</textarea>
                                </th>
                            <#else>
                                <th id="${elementParamName!}_${headers[key]['value']?html}" style="${width}">${headers[key]['label']!?html}</th>
                            </#if>
                        </#list>
                        <th class="grid-action-header"></th>
                    </tr>
                </thead>
            </#if>
            <tbody>
            	<#-- row template -->
	            <tr id="grid-row-template" class="grid-row-template" style="display:none;">
	                <#if element.properties.showRowNumber?? && element.properties.showRowNumber! != "">
	                    <td><span class="grid-cell rowNumber"></span></td>
	                </#if>
		            <#list headers?keys?sort as key>
		                <td style="text-align: ${headers[key]['alignment']};" ><span id="${elementParamName!}_${headers[key]['value']?html}"  name="${elementParamName!}_${headers[key]['value']?html}" column_key="${headers[key]['value']?html}" column_type="${headers[key]['formatType']!?html}" column_format="${headers[key]['format']!?html}" class="grid-cell"></span></td>
		            </#list>
	                <td style="display:none;"><textarea id="${elementParamName!}_jsonrow"></textarea></td>
	            </tr>

	            <#-- row data -->
	            <#list rows as row>
	                <tr class="grid-row" id="${elementParamName!}_row_${row_index}">
	                    <#if element.properties.showRowNumber?? && element.properties.showRowNumber! != "">
	                        <td><span class="grid-cell rowNumber">${row_index + 1}</span></td>
	                    </#if>
		                <#list headers?keys?sort as key>
		                    <td style="text-align: ${headers[key]['alignment']};" >
		                    	<span id="${elementParamName!}_${headers[key]['value']?html}" name="${elementParamName!}_${headers[key]['value']?html}" column_key="${headers[key]['value']?html}" column_type="${headers[key]['formatType']!?html}" column_format="${headers[key]['format']!?html}" class="grid-cell">
		                            <#attempt>
		                            	<#if headers[key]['formatType'] == 'form' && headers[key]['format']??>
		                            		<!-- form -->
		                            		<#if row[headers[key]['value'] + '_label_formattachment']??>
		                            			<a href="#" id="${elementParamName!}_${headers[key]['value']?html}" class="grid-action-display-attachment-form" data-rowindex=${row_index}>${element.formatColumn(headers[key]['value'], headers[key], row["id"], row[headers[key]['value'] + '_label_formattachment'], appId, appVersion, request.contextPath)}</a>
		                            		<#else>
		                            			<i>${element.formatColumn(headers[key]['value'], headers[key], row["id"], row[headers[key]['value']], appId, appVersion, request.contextPath)}</i>
		                            		</#if>                        		
		                            	<#else>
		                            		<!-- text, file, image -->
		                                	${element.formatColumn(headers[key]['value'], headers[key], row["id"], row[headers[key]['value']], appId, appVersion, request.contextPath)}
		                                </#if>
		                            <#recover>
		                            	<!-- recover -->
		                                ${row[headers[key]['value']]!?html}
		                            </#attempt>
		                        </span>
		                        
		                        <#if headers[key]['formatType'] == 'form' && row[headers[key]['value'] + '_jsonrow_formattachment']??>
		                    		<textarea style="display:none;" id="${elementParamName!}_${headers[key]['value']?html}_jsonrow_formattachment" name="${elementParamName!}_${headers[key]['value']?html}_jsonrow_formattachment_${row_index}">${row[headers[key]['value'] + '_jsonrow_formattachment']?html}</textarea>
		                    	</#if>
		                    </td>
		                </#list>
	                    <td style="display:none;"><textarea id="${elementParamName!}_jsonrow" name="${elementParamName!}_jsonrow_${row_index}">${row['jsonrow']!?html}</textarea></td>
	                </tr>
	            </#list>

	            <#if (summary?size > 0) >
		            <tr class="grid-summary" id="${elementParamName!}_summary">
			            <#list headers?keys?sort as key>
			            	<#assign
			            		fieldId = headers[key]['value']
			            		columnType = headers[key]['formatType']
			            		columnFormat = headers[key]['format'] >

			            	<#if summary[fieldId]?? >
				            	<td class="grid-summary" style="background: beige; text-align: ${headers[key]['alignment']}; font-weight:bolder;" >
				            		<span
					            			id="${elementParamName!}_${fieldId?html}"
					            			name="${elementParamName!}_${fieldId?html}"
					            			column_type="${columnType!?html}"
					            			column_format="${columnFormat!?html}"
					            			class="grid-summary">

					            		<#if (rows?size > 0) >
					            			${element.formatColumn(fieldId, headers[key], "", summary[fieldId], appId, appVersion, request.contextPath)}
					            		</#if>
				            		</span>
				            	</td>
				            <#else>
				            	<td class="grid-summary" style="background: beige;" />
			            	</#if>
			            </#list>
			            <td style="display:none;"><textarea id="${elementParamName!}_jsonoption" name="${elementParamName!}_jsonoption">${jsonOption!?html}</textarea></td>
		            </tr>
		    	</#if>
            </tbody>
        </table>
    </div>
</div>
