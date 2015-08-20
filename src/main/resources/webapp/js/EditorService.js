app.service('EditorService', function() {
  var editors = {};

  var createEditorFor = function(divId) {
    var editor = ace.edit(divId);
    editor.setTheme("ace/theme/github");
    editor.getSession().setMode("ace/mode/scala");
    editors[divId] = editor;
    return editor;
  };

  var getEditorFor = function(divId) {
    return editors[divId];
  };

  return {
    createEditorFor: createEditorFor,
    getEditorFor: getEditorFor
  };
});
