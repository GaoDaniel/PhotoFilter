import * as React from 'react';
import {useState}  from 'react';
import {StyleSheet, SafeAreaView, View, Image, ScrollView} from 'react-native';
import {Camera, CameraPermissionStatus, useCameraDevices} from 'react-native-vision-camera';

export default function TabOneScreen() {
  const devices = useCameraDevices();
  const device = devices.back;
  const [active, setActive] = useState(false);

  let openCamera = async () => {
    let permissionResult = await Camera.requestCameraPermission();

    if (permissionResult != 'authorized') {
      return;
    }
    setActive(true);
  }
  return (
    <SafeAreaView style={styles.container}>
      <Camera 
        style={StyleSheet.absoluteFill}
        device={device}
        photo={true}
        isActive={active}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'aliceblue',
  },
  buttonContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginVertical: 8,
  },

  image: {
    marginVertical: 24,
    alignItems: 'center',
  },
});