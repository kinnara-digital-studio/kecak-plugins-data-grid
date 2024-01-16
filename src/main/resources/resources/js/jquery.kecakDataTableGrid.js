(function( $ ){
    let contextPath;
    let javascriptEnhancementOnFormLoad = [];

    let methods = {
        init: function(args) {
            let $element = $(this);
            let $table = $(this).find("table");

            args['rowId'] = 'id';

            if(!args.properties.disabledAdd && !args.properties.readonly) {
                args['buttons'] = [{
                    text: 'Add',
                    action: function ( e, dt, node, config ) {
                        methods.add.apply($element);
                    }
                }];
            } else {
                args['buttons'] = [];
            }

            args['dom'] = 'Bfrtip';

            args['footerCallback'] = methods.footerCallback;

            args.columns.forEach((e, i) => e['createdCell'] = methods.createdCell);

            args['createdRow'] = methods.createdRow;

            args['paging'] = false;

            return $table.DataTable(args);
        },

        getFrameId: function(id) {
            return "formGridFrame_" + id;
        },

        initPopupDialog: function(args){
            contextPath = args.contextPath;

            var frameId = methods.getFrameId($(this).attr("id"));
            javascriptEnhancementOnFormLoad[frameId] = args.javascriptEnhancementOnFormLoad;

            var width = $(this).find('#width').val();
            var height = $(this).find('#height').val();

            JPopup.create(frameId, args.title, width, height);
        },

        add: function() {
            let $element = $(this);
            return this.each(function(){
                let elementId = $element.attr('id');
                let frameId = methods.getFrameId(elementId);
                let $addButton = $element.find('.grid-action-add')
                javascriptEnhancementOnFormLoad[frameId]($element, $addButton);

            	let defaultValues = JSON.parse($element.find("#defaultValues").val());
            	let formUrl = $element.find('#formUrl').val();
            	let formJson = $element.find('#json').val();
            	let nonce = $element.find('#nonce').val();
            	let height = $element.find('#height').val();
            	let width = $element.find('#width').val();
                methods.popupForm(elementId, formUrl, formJson, nonce, elementId + "_add", {}, defaultValues, height, width);
            });
        },

        edit: function($row, data) {
            let $element = $(this);
            let $editButton = $row.find('.dataTable-edit');
            let elementId = $element.attr('id');
            let frameId = methods.getFrameId(elementId);
            return this.each(function() {
                javascriptEnhancementOnFormLoad[frameId]($element, $editButton);

                let defaultValues = JSON.parse($element.find("#defaultValues").val());
                for(var i in defaultValues) {
                    if(!data[i] || data[i] == "")
                        data[i] = defaultValues[i];
                }

                let formUrl = $element.find('#formUrl').val();
            	let formJson = $element.find('#json').val();
            	let nonce = $element.find('#nonce').val();
            	let height = $element.find('#height').val();
            	let width = $element.find('#width').val();
            	let rowId = $row.attr('id');
            	let setting = { rowId : rowId };
                methods.popupForm(elementId, formUrl, formJson, nonce, elementId + "_edit", setting, data, height, width);
            });
        },

        popupForm: function(id, url, json, nonce, callback, setting, datas, height, width){
            if (datas != undefined ) {
                if (datas.id) {
                    if (url.indexOf("?") != -1) {
                        url += "&";
                    } else {
                        url += "?";
                    }
                    url += "id=" + datas.id;
                }
                url += UI.userviewThemeParams();
            }

            let value = $($.parseHTML(datas.jsonrow)).val();

            let params = {
                _json : json,
                _callback : callback,
                _setting : JSON.stringify(setting),
                _jsonFormData : value,
                _nonce : nonce
            };

            let frameId = methods.getFrameId(id);
            JPopup.show(frameId, url, params, "", width, height);
        },

        addRow: function(data){
            return $(this).each(function(){
                let oTable = $(this).find('table').dataTable().api();
                let node = oTable.row.add(data).draw().node();

                var frameId = methods.getFrameId($(this).attr('id'));
                JPopup.hide(frameId);
                $(this).change();
            });
        },

        editRow: function($row, data){
            return $(this).each(function(){
                var frameId = methods.getFrameId($(this).attr('id'));

                if ($(this).hasClass("readonly")) {
                    JPopup.hide(frameId);
                    return;
                }

                let oTable = $(this).find('table').dataTable().api();
                oTable.row($row).data(data).draw()

                JPopup.hide(frameId);
                $(this).change();
            });
        },

        checkDuplicate : function (container, args) {
            var okToInsert = true;
            var uniqueKey = $(container).find('#uniqueKey').val();
            if (uniqueKey && uniqueKey != null) {
                // find existing row
                var obj = eval("[" + args.result + "]");
                var uniqueVal = obj[0][uniqueKey];
                if (uniqueVal) {
                    $(container).find(".grid-cell[column_key=" + uniqueKey + "]").each(function() {
                        if (args.rowId && args.rowId != null) {
                            var row = $(this).parent().parent();
                            if ($(row).attr("id") == args.rowId) {
                                return true;
                            }
                        }
                        if ($.trim($(this).text()) == uniqueVal) {
                            okToInsert = false;
                            return false;
                        }
                    })
                }
            }
            return okToInsert;
        },

        showHidePlusIcon : function (container) {
            var row = $(container).find('#validateMaxRow').val();
            if (row && row != null) {
                var rowcount = $(container).find("tr").length - 2;
                if (rowcount >= parseInt(row)) {
                    $(container).find(".grid-action-add").hide();
                } else {
                    $(container).find(".grid-action-add").show();
                }
            }
        },

        deleteRow: function() {
            return $(this).each(function(){
                $(this).change();
            });
        },

        formatValue : function(type, format, fieldname, value) {
            if(type == undefined) {
                return value
            } else {
                if (type == "html") {
                    return value;
                } else if(type == "divTag") {
                    return value.replace(/^/, "<div class='grid-bubble'>").replace(/;/g, "</div><div>").replace(/$/, "</div>");
                } else if ((type == "form" || type == "file") && value != undefined){
                    return "<i>" + value + "</i>";
                } else if (type == "image" && format != undefined && format != "" && value != "") {
                    try{
                        var formDefId = format;
                        var recordId = obj[0]["id"];
                        var temp = obj[0]["_tempFilePathMap"];
                        var tempFile = false;
                        if (temp != undefined && temp[fieldname] != undefined) {
                            tempFile = true;
                        }
                        if (recordId != undefined && recordId != "" && !tempFile) {
                            var appId = $(element).find('#appId').val();
                            var appVersion = $(element).find('#appVersion').val();
                            var contextPath = $(element).find('#contextPath').val();
                            var filePath = contextPath + "/web/client/app/" + appId + "/" + appVersion + "/form/download/" + formDefId + "/" + recordId + "/" + value;
                            if (type == "image") {
                                var imgfilePath = filePath + ".thumb.jpg.";
                                filePath += ".";
                                value = "<a href=\""+filePath+"\" target=\"_blank\" ><img src=\""+imgfilePath+"\"/></a>";
                            } else {
                                filePath += ".?attachment=true";
                                value = "<a href=\""+filePath+"\" target=\"_blank\" >"+value+"</a>";
                            }
                            return value;
                        } else {
                            return value;
                        }
                    }catch(e){
                        return value;
                    }
                } else if (type == "decimal" && format != undefined && format != ""  && (!isNaN(value) || value == "")) {
                    try{
                        if (value == "") {
                            value = "0";
                        }
                        value = parseFloat(value);
                        value = value.toFixed(parseInt(format));
                    }catch(e){}

                    return value;
                } else if (type == "date" && format != undefined && format != "" && value != "") {
                    try{
                        var dateFormat = format.split('|');
                        if (dateFormat.length == 2) {
                            var d = new Date(getDateFromFormat(value, methods.getDateFormat(dateFormat[0])));
                            value = formatDate(d ,methods.getDateFormat(dateFormat[1]));
                        }
                    }catch(e){}

                    return value;
                } else if(type == "cint") {
                	if(typeof value === 'string')
                		value = value.replace(/,/g, "");

                    try {
                        if(format.match(/,/)) {
                            return sprintf("%" + format.replace(/,/g,"") + "d", value).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
                        } else {
                            return sprintf("%" + format + "d", value);
                        }

                    } catch(e) { }
                } else if(type == "cfloat") {
                	if(typeof value === 'string')
            			value = value.replace(/,/g, "");
                	try {
                        if(format.match(/,/)) {
                            return sprintf("%" + format.replace(/,/g,"") + "f", value).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
                        } else {
                            return sprintf("%" + format + "f", value);
                        }

                    } catch(e) { }
                } else if(value === undefined) {
                    return "Error Formatting";
                } else {
                    return value;
                }
            }
        },

        getDateFormat: function(format) {
            if (format.indexOf("MMMMM") !== -1) {
                format = format.replace("MMMMM", "MMM");
            } else if (format.indexOf("MMM") !== -1) {
                format = format.replace("MMM", "NNN");
            }
            return format;
        },

        createdCell : function ( cell, cellData, rowData, rowIndex, colIndex ) {
            let api = this.api();
            let gridName = $(this).attr('data-name');
            api.settings().each(function(setting, i) {
                let colDef = setting.aoColumns[colIndex];
                let fieldName = colDef.name;
                $(cell).attr('name', gridName + '_' + fieldName);
            });

        },

        createdRow : function( row, rowData, rowIndex, cells ) {
            let api = this.api();
        },

        footerCallback : function( row, data, start, end, display ) {
            let api = this.api();
            $(row).find('th.summary-sum').each(function(i, e) {
                let fieldName = $(e).attr('name');
                let col = api.column(fieldName + ':name');
                let data = col.data();
                let summaryValue = data.reduce((a, b) => parseFloat(a.toString().replace(/,/g, "")) + parseFloat(b.toString().replace(/,/g, "")), 0);
                let format = $(e).attr('data-format');
                let type = $(e).attr('data-formatType');
                let formattedValue = methods.formatValue(type, format, fieldName, summaryValue);
                $(e).html(formattedValue);
            });
        },

        updateSummary : function(table) {
        	var records = $(table).find("tr.grid-row td textarea[id$='jsonrow']");
        	var summaryRow = $(table).find("tr.grid-summary");
        	var jsonOption = JSON.parse($(summaryRow).find("td textarea[id$='jsonoption']").val());

        	if(records.size() <= 0) {
        		for(var field in jsonOption) {
        			var cell = $(summaryRow).find("td.grid-summary span[id$='" + field + "']");
        			$(cell).text("");
	    			//methods.formatValue(cell, field, "");
        		}
        	} else {
	        	for(var field in jsonOption) {
	        		var operation = jsonOption[field];
	        		var result = 0;

	    			if(operation == "sum") {
	    				$(records).each(function() {
	    	        		var jsonRow = JSON.parse($(this).val());
	    	        		if(jsonRow[field] != undefined)
	    	        			result += (jsonRow[field] == undefined ? 0 : parseFloat(jsonRow[field].toString().replace(/,/g, "")));
	    	        	});
	    			} else if(operation == "count"){
	    				$(records).each(function() {
	    	        		result++;
	    	        	});
	    			} else if(operation == "avg") {
	    				var sum = 0;
	    				var count = 0;
	    				$(records).each(function() {
	    	        		var jsonRow = JSON.parse($(this).val());
	    	        		sum += (jsonRow[field] == undefined ? 0 : parseFloat(jsonRow[field].toString().replace(/,/g, "")));
	    	        		count++;
	    	        	});
	    				result = count > 0 ? sum / count : 0;
	    			} else if(operation == "max") {
	    				var arr = [];
	    				$(records).each(function() {
	    					var jsonRow = JSON.parse($(this).val());
	    					arr.push(jsonRow[field] == undefined ? 0 : parseFloat(jsonRow[field].toString().replace(/,/g, "")));
	    	        	});
	    				result = Math.max(...arr);
	    			} else if(operation == "min") {
	    				var arr = [];
	    				$(records).each(function() {
	    					var jsonRow = JSON.parse($(this).val());
	    	        		arr.push(jsonRow[field] == undefined ? 0 : parseFloat(jsonRow[field].toString().replace(/,/g, "")));
	    	        	});
	    				result = Math.min(...arr);
	    			}

	    			var cell = $(summaryRow).find("td.grid-summary span[id$='" + field + "']");
	    			if(operation == "count")
	    				$(cell).text(result);	// count is barely an integer, no need formatting
	    			else
	    				methods.formatValue(cell, field, result);
	        	}
        	}
        }
    };

    $.fn.kecakDataTableFormGrid = function( method ) {
        if ( methods[method] ) {
            return methods[method].apply( this, Array.prototype.slice.call( arguments, 1 ));
        } else if ( typeof method === 'object' || !method ) {
            let ret = methods.init.apply( this, arguments );
            return ret;
        } else {
            $.error ( 'Method ' +  method + ' does not exist on jQuery.kecakDataTableFormGrid' );
        }
    };

})( jQuery );

