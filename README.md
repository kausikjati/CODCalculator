# COD Calculator Android App

A native Android starter app for recording delivery-boy COD deposits and calculating dues.

## Features

- Delivery-boy picker designed to be populated from a Google Sheet after Drive/Sheets OAuth authorization.
- Numeric fields for total COD collected, cash deposited, and online deposited.
- Due calculation: `total - cash - online = due`.
- Date-stamped COD entry model ready to append to Google Sheets.
- Delivery-boy due section grouped by delivery-boy name.

## Google Sheets integration plan

The current `GoogleSheetRepository` interface isolates data access from the Android UI. Replace `InMemoryGoogleSheetRepository` with a Sheets API implementation that:

1. Authorizes the user for Google Drive/Sheets access.
2. Reads delivery boys from a `DeliveryBoys` sheet with `id` and `name` columns.
3. Appends every deposit to a `CodEntries` sheet with date, delivery-boy, total, cash, online, and due columns.
4. Reads entries back to show each delivery boy's total due amount.


## Run in Android Studio

1. Open this repository folder in Android Studio.
2. Let Android Studio sync the Gradle project using its bundled Gradle support.
3. Select the checked-in `app` run configuration.
4. Choose an emulator or physical Android device and click Run.

The `.idea/runConfigurations/app.xml` file is committed so Android Studio shows a ready-to-run `app` configuration after project sync. Gradle wrapper files are intentionally not committed because this code review environment does not support binary files such as `gradle-wrapper.jar`.
