const parameterField = document.getElementById('parameterNames');
let textParameterNames = document.getElementById('type').value === 'text' ? parameterField.value : '';

function updateGeneratorParameters() {
    const help = document.getElementById('aqi-settings-help');
    if (help) help.hidden = document.getElementById('type').value !== 'generated'
        || document.getElementById('generatorName').value !== 'aqi-forecast';
    if (document.getElementById('type').value !== 'generated') return;
    const selected = document.getElementById('generatorName').selectedOptions[0];
    const names = selected?.dataset.parameterNames || '';
    parameterField.value = names ? names.split(',').join('\n') : '';
}

function showType() {
    const text = document.getElementById('type').value === 'text';
    document.getElementById('text-fields').hidden = !text;
    document.getElementById('generated-fields').hidden = text;
    if (!text && !parameterField.readOnly) textParameterNames = parameterField.value;
    parameterField.readOnly = !text;
    if (text) parameterField.value = textParameterNames;
    else updateGeneratorParameters();
    const help = document.getElementById('aqi-settings-help');
    if (text && help) help.hidden = true;
}
showType();
