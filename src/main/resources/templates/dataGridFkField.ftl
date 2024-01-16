<#if includeMetaData>
    <div class="form-cell" ${elementMetaData!}>
        <label class="label">${element.properties.id}</label>
        <span class="form-floating-label">Data Grid FK</span>
        <input id="${elementParamName!}" name="${elementParamName!}" type="text" value="${value!?html}" readonly />
    </div>
<#else>
    <input id="${elementParamName!}" name="${elementParamName!}" type="hidden" value="${value!?html}"/>
</#if>
