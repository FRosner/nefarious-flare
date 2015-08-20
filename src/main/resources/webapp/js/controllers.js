var app = angular.module('nefarious-app', ['restangular', 'ui.bootstrap']);

app.controller('StageOverviewController', function ($scope, Restangular, $modal, $log) {
  Restangular.setBaseUrl("/rest");

  var allStages = Restangular.all('stages');
  allStages.getList().then(function(stageIds) {
    $scope.stages = {};
    stageIds.map(function(stageId) {
      Restangular.one('stages', stageId).get().then(function(stage) {
        $scope.stages[stageId] = stage;
      });
    });
  });

  $scope.enableDragging = function() {
    $(function() {
      $( ".draggable" ).draggable();
    });
  }

  $scope.open = function(stage) {
    var modalInstance = $modal.open({
      animation: true,
      templateUrl: 'editStage.html',
      controller: 'EditStageController',
      size: 'lg',
      resolve: {
        stage: function () {
          return stage;
        }
      }
    });

    modalInstance.result.then(function (updatedStage) {
      updatedStage.save();
      $scope.stages[updatedStage.id] = updatedStage;
    }, function () {
      // do nothing if dismissed
    });
  };

  $scope.delete = function(stage) {
    stage.remove();
    delete $scope.stages[stage.id];
  };
});

app.controller('EditStageController', function ($scope, $modalInstance, stage, EditorService) {
  $scope.stage = stage;

  $scope.ok = function (passed) {
    stage.name = document.getElementById("stageName").value;
    stage.code = EditorService.getEditorFor("stageCode").getValue();
    $modalInstance.close(stage);
  };

  $scope.cancel = function () {
    $modalInstance.dismiss();
  };
});

app.controller('CodeEditorController', function ($scope, EditorService) {
  var editor = EditorService.createEditorFor("stageCode");
  $scope.setCode = function(code) {
    editor.setValue(code);
  };
});
