(function( $ ){
    var contextPath;
    var javascriptEnhancementOnFormLoad = [];

    var methods = {
        init: function() {
            return this.each(function(){
                var thisObj = $(this);
                
                if (jQuery.browser.msie && jQuery.browser.version === '9.0'){
                    var table = $(this).find("table");
                    var expr = new RegExp('>[ \t\r\n\v\f]*<', 'g');
                    var response_html_fixed = table.html().replace(expr, '><'); //A disgusting hack for ie9. Removes space between open and close <td> tags
                    $(table).html(response_html_fixed.trim());
                }
                
                $(this).find(".grid-row").each(function(rowIndex, row) {
                    var json = $(row).find("textarea[id$=_jsonrow]").val();
                    methods.decorateRow(row);
                    //methods.fillValue(row, json);
                    methods.updateRowIndex(row, rowIndex);
                });
                methods.disabledMoveAction($(this).find('table'));
                
                // add new row button
                if ($(this).find(".grid-action-add").length == 0) {
                    var link = $('<a class="grid-action-add" href="#" title="Add Row"><span>Add Row</span></a>');
                    link.click(function() {
                        var table = $(this).parent();
                        methods.add.apply(thisObj, arguments);
                        return false;
                    });
                    $(this).append(link);
                }

                $(this).find("a.grid-action-display-attachment-form").click(function() {
                    var columnId = this.id;
                    var container = $(this).parent().parent().parent().parent().parent().parent().parent();
                    var containerId = $(container).attr('id');
                    var json = $(container).find("th#" + columnId + " textarea.jsonform_formattachment").val();
                    var url = $(container).find("#attachmentFormUrl").val();
                    var nonce = $(container).find("#nonce").val();
                    var callback = containerId + "_display_form_attachment";
                    var value = $(container).find("textarea[name='" + columnId + "_jsonrow_formattachment_" + $(this).attr("data-rowindex") + "']").val();
                    var height = $(container).find("#height").val();
                    var width = $(container).find("#width").val();
                    var setting = "{}";

                    methods.popupForm(containerId, url, json, nonce, callback, setting, value, height, width);
                });
            });
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
            return this.each(function(){
                javascriptEnhancementOnFormLoad[methods.getFrameId($(this).attr('id'))]($(this), $(this).find('.grid-action-add'));

            	var defaultValues = $(this).find("#defaultValues").val();
                methods.popupForm($(this).attr('id'), $(this).find('#formUrl').val(), $(this).find('#json').val(), $(this).find('#nonce').val(), $(this).attr('id')+"_add", "{}", defaultValues, $(this).find('#height').val(), $(this).find('#width').val());
            });
        },
        
        edit: function() {
            let row = $(this).parents('tr.grid-row');
            let container = $(this).parents('div.grid');

            javascriptEnhancementOnFormLoad[methods.getFrameId($(container).attr('id'))]($(container), this);

            let defaultValues = JSON.parse($(container).find("#defaultValues").val());
            let jsonRow = JSON.parse($(row).find('textarea[id$=_jsonrow]').val());
            //let rowValues = jsonRow._tempRequestParamsMap ? jsonRow._tempRequestParamsMap : jsonRow;
            let rowValues = jsonRow;

            for(let i in defaultValues) {
                if(!rowValues[i] || rowValues[i] == "")
                    rowValues[i] = defaultValues[i];
            }

            methods.popupForm($(container).attr('id'), $(container).find('#formUrl').val(), $(container).find('#json').val(), $(container).find('#nonce').val(), $(container).attr('id')+"_edit", "{rowId:'"+$(row).attr('id')+"'}", JSON.stringify(rowValues), $(container).find('#height').val(), $(container).find('#width').val());
        },

        popupForm: function(id, url, json, nonce, callback, setting, value, height, width){

            if (value != undefined && value != '') {
                var datas = eval("(" + value + ")");

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

            var params = {
                _json : json,
                _callback : callback,
                _setting : setting,
                //_jsonFormData : value,
                _jsonFormData : JSON.stringify(datas),
                _nonce : nonce
            };

            var frameId = methods.getFrameId(id);
            JPopup.show(frameId, url, params, "", width, height);
        },
        
        addRow: function(args){
            return $(this).each(function(){
                var frameId = methods.getFrameId($(this).attr('id'));
                
                if (methods.checkDuplicate(this, args) && !$(this).hasClass("readonly")) {
                    // get table
                    var table = $(this).find("table");

                    // clone template
                    var template = $(table).find(".grid-row-template");
                    var newRow = $(template).clone();
                    newRow.removeClass("grid-row-template");
                    newRow.css('display','');
                    newRow.addClass("grid-row");

                    methods.decorateRow(newRow);
                    methods.fillValue(this, newRow, args.result);
                    
                    // append row
                    table.append(newRow);
                    
                    var summaryRow = table.find("tr.grid-summary").clone();
                    if(summaryRow != undefined && summaryRow.length > 0) {
                    	// summary row found
                    	
	                    // remove summary
	                    table.find("tr.grid-summary").remove();
	                    
	                    // append summary
	                    table.append(summaryRow);
	                    
	                    methods.updateSummary(table);
                    }
	                    
                    // set input names and values
                    var rowIndex = $(table).find("tr.grid-row").length-1;
                    methods.updateRowIndex(newRow, rowIndex);
                    methods.disabledMoveAction($(newRow).parent().parent());

                    JPopup.hide(frameId);

                    // trigger change
                    $(this).trigger("change");
                    methods.showHidePlusIcon(this);
                } else {
                    JPopup.hide(frameId);
                }
            });
        },
        
        editRow: function(args){
            return $(this).each(function(){
                var frameId = methods.getFrameId($(this).attr('id'));
                
                if (methods.checkDuplicate(this, args) && !$(this).hasClass("readonly")) {
                    // get table
                    var table = $(this).find("table");
                    var row = $(table).find("#"+args.rowId);

                    methods.fillValue(this, row, args.result);
                    
                    // update summary row
                    var summaryRow = table.find("tr.grid-summary");
                    if(summaryRow != undefined && summaryRow.length > 0) {
                    	methods.updateSummary(table);
                    }

                    JPopup.hide(frameId);

                    // trigger change
                    $(this).trigger("change");
                } else {
                    JPopup.hide(frameId);
                }
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
            var row = $(this).parent().parent();
            var table = $(row).parent().parent();
            var container = $(table).parent();
            var deleteMessage = $(container).find('#deleteMessage').val();
            
            if (deleteMessage == "") {
                deleteMessage = "Delete row?";
            }
            if (confirm(deleteMessage)) {
                row.remove();
                
                // reset input names
                methods.updateAllRowIndex(table);
                methods.disabledMoveAction(table);
                
                // update summary row
                var summaryRow = table.find("tr.grid-summary");
                if(summaryRow != undefined && summaryRow.length > 0) {
                	methods.updateSummary(table);
                }
                
                // trigger change
                var el = table.parent();
                $(el).trigger("change");
                methods.showHidePlusIcon(el);
            }
        },
        
        moveRowUp: function() {
            var currentRow = $(this).parent().parent();
            var prevRow = $(currentRow).prev();
            if(prevRow.attr("id") != "grid-row-template"){
                $(currentRow).after(prevRow);
                methods.updateAllRowIndex($(currentRow).parent().parent());
                methods.disabledMoveAction($(currentRow).parent().parent());
            }
        },
        
        moveRowDown: function() {
            var currentRow = $(this).parent().parent();
            var nextRow = $(currentRow).next();
            if(nextRow.length > 0){
                $(nextRow).after(currentRow);
                methods.updateAllRowIndex($(currentRow).parent().parent());
                methods.disabledMoveAction($(currentRow).parent().parent());
            }
        },
        
        disabledMoveAction: function(table) {
            $(table).find('a.grid-action-moveup').removeClass("disabled");
            $(table).find('a.grid-action-moveup:eq(0)').addClass("disabled");

            $(table).find('a.grid-action-movedown').removeClass("disabled");
            $(table).find('a.grid-action-movedown:last').addClass("disabled");
        },
        
        decorateRow: function(row) {
        	if(!$(row).hasClass("grid-summary")) {
	            var td = $('<td class="grid-action-cell"></td>');
	            $(td).append('<a class="grid-action-edit" href="#" title="Edit"><span>Edit Row</span></a>');
	            $(td).append('<a class="grid-action-delete" href="#" title="Delete"><span>Delete Row</span></a>');
	            $(td).append('<a class="grid-action-moveup" href="#" title="Move Up"><span>Move Up</span></a>');
	            $(td).append('<a class="grid-action-movedown" href="#" title="Move Down"><span>Move Down</span></a>');
	            $(td).find('.grid-action-edit').click(function() {
	                methods.edit.apply(this, arguments);
	                return false;
	            });
	            $(td).find('.grid-action-delete').click(function() {
	                methods.deleteRow.apply(this, arguments);
	                return false;
	            });
	            $(td).find('.grid-action-moveup').click(function() {
	                methods.moveRowUp.apply(this, arguments);
	                return false;
	            });
	            $(td).find('.grid-action-movedown').click(function() {
	                methods.moveRowDown.apply(this, arguments);
	                return false;
	            });
	            
	            $(row).append(td);
        	}
        },
        
        fillValue: function(element, row, json) {
            var obj = eval("["+json+"]");
            $(row).find('span.grid-cell').each(function(){
                var column = $(this).attr("column_key");
                var value = obj[0][column];
                
                methods.formatValue(this, column, value);
            });

            $(row).find('textarea[id$="_jsonrow"]').each(function(){
                $(this).html(json);
            });
        },
        
        formatValue : function(cell, fieldname, value) {
            var type = $(cell).attr("column_type");
            var format = $(cell).attr("column_format");
            
            if(type == undefined) {
                $(cell).text(value);
            } else {
                if (type == "html") {
                    $(cell).html(value);
                } else if(type == "divTag") {
                    $(cell).html(value.replace(/^/, "<div class='grid-bubble'>").replace(/;/g, "</div><div>").replace(/$/, "</div>"));
                } else if ((type == "form" || type == "file") && value != undefined){
                    $(cell).html("<i>" + value + "</i>");
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
                            $(cell).html(value);
                        } else {
                            $(cell).text(value);
                        }
                    }catch(e){
                        $(cell).text(value);
                    }
                } else if (type == "decimal" && format != undefined && format != ""  && (!isNaN(value) || value == "")) {
                    try{
                        if (value == "") {
                            value = "0";
                        }
                        value = parseFloat(value);
                        value = value.toFixed(parseInt(format));
                    }catch(e){}

                    $(cell).text(value);
                } else if (type == "date" && format != undefined && format != "" && value != "") {
                    try{
                        var dateFormat = format.split('|');
                        if (dateFormat.length == 2) {
                            var d = new Date(getDateFromFormat(value, methods.getDateFormat(dateFormat[0])));
                            value = formatDate(d ,methods.getDateFormat(dateFormat[1]));
                        }
                    }catch(e){}
                    
                    $(cell).text(value);
                } else if(type == "cint") {
                	if(typeof value === 'string')
                		value = value.replace(/,/g, "");
                	
                    try {
                        if(format.match(/,/)) {
                            $(cell).text(sprintf("%" + format.replace(/,/g,"") + "d", value).replace(/\B(?=(\d{3})+(?!\d))/g, ","));
                        } else {
                            $(cell).text(sprintf("%" + format + "d", value));
                        }

                    } catch(e) { }                        
                } else if(type == "cfloat") {
                	if(typeof value === 'string')
            			value = value.replace(/,/g, "");

                	try {
                        if(format.match(/,/)) {
                            $(cell).text(sprintf("%" + format.replace(/,/g,"") + "f", value).replace(/\B(?=(\d{3})+(?!\d))/g, ","));
                        } else {
                            $(cell).text(sprintf("%" + format + "f", value));
                        }

                    } catch(e) { }
                } else if(value === undefined) {
                    $(cell).text("");
                } else {
                    $(cell).text(value);
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
        
        updateRowIndex: function(row, rowIndex){
            var id = $(row).parent().parent().parent().attr('id');
            
            $(row).attr("id", id+"_row_"+rowIndex);
            $(row).find("textarea[id$='_jsonrow']").each(function(){
                $(this).attr("name", $(this).attr("id")+"_"+rowIndex);
            });
            
            $(row).find("span.grid-cell.rowNumber").text(rowIndex + 1);
            
            //update row even/odd css class
            $(row).removeClass("odd");
            $(row).removeClass("even");
            if(rowIndex % 2 == 0){
                $(row).addClass("even");
            }else{
                $(row).addClass("odd");
            }
        },
        
        updateAllRowIndex: function(table) {
            $(table).find(".grid-row").each(function(rowIndex, row) {
                methods.updateRowIndex(row, rowIndex);
            });
        },
        
        refreshIndex: function() {
            return this.each(function(){
                var thisObj = $(this);
                var table = thisObj.find("table");
                
                methods.updateAllRowIndex(table);
                methods.disabledMoveAction(table);
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

    $.fn.kecakdatagrid = function( method ) {

        if ( methods[method] ) {
            return methods[method].apply( this, Array.prototype.slice.call( arguments, 1 ));
        } else if ( typeof method === 'object' || ! method ) {
            return methods.init.apply( this, arguments );
        } else {
            $.error( 'Method ' +  method + ' does not exist on jQuery.kecakdatagrid' );
        }

    };

})( jQuery );

