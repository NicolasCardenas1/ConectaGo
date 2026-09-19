module com.duoc.lims.limsclient {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.duoc.lims.limsclient to javafx.fxml;
    exports com.duoc.lims.limsclient;
}