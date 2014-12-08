#Labelling App

Implementation of an data mining app using
[FunF](https://code.google.com/p/funf-open-sensing-framework/)

The app additionally enables the user to provide ground truth data via labels

It is built with new [android build
system](http://tools.android.com/tech-docs/new-build-system) based on
[gradle](http://www.gradle.org/).

Because of this the recommended IDE is the new [Android
Studio](http://developer.android.com/sdk/installing/studio.html) which uses
gradle.

##Setup local build environment

###Using gradle

You should the included Gradle Wrapper (`gradlew`) to execute all tasks, or a
local install. 

Additionally an environment variable named `ANDROID_HOME` or a
`local.properties` file is required:

>Note: You will also need a local.properties file to set the location of the SDK
>in the same way that the existing SDK requires, using the sdk.dir property.
>Alternatively, you can set an environment variable called ANDROID_HOME.

For an overview over the available tasks refer to the [Gradle Plugin User
Guide](http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Build-Tasks)

####Additional Gradle Tasks

* `gradlew test` runs all unit tests based on
  [robolectric](http://robolectric.org/). Robolectric tests do not need an
emulator to execute and are therefore faster and enable TDD
    * Note: Robolectric tests are provided by the [gradle-android-test-plugin](https://github.com/square/gradle-android-test-plugin)
    * Test results are in `<module-folder>/build/test-report`

### Using Android Studio

1. Download and install [Android Studio](http://developer.android.com/sdk/installing/studio.html)
3. Import project

    You may need to install the latest build tools (Android SDK Build-tools Rev. 21.1.1)and SDK (API Level 21) beforehand
    
    1. Start Android Studio
    2. Choose "Import Project..."
    3. Navigate to this directory
    4. "OK"
    5. "Use default gradle wrapper (recommended)" should be selected
    6. "OK"

4. *Done*


