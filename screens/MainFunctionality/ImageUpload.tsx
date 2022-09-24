import {Image, View, TouchableOpacity} from 'react-native';
import React from 'react';
import * as ImagePicker from 'expo-image-picker';
import {styles} from "../TabTwoScreen";


export default function ImageUpload(props: { makeAlert: (title: string, message: string) => void, callback: (res: string) => void }) {

  /**
   * Opens the camera roll on the device, alerting user if access is denied
   */
  let openImagePickerAsync = async () => {
    let permissionResult = await ImagePicker.requestMediaLibraryPermissionsAsync();

    if (!permissionResult.granted) {
      props.makeAlert("Access Denied", "Permission to access camera roll is required!");
      return;
    }

    let pickerResult = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [4, 3],
      quality: 1,
      base64: true,
      exif: true
    });
    if (!pickerResult.cancelled && pickerResult.base64) {
      props.callback(pickerResult.base64)
      console.log("exif " + pickerResult.exif);
    }
  }

  /**
   * Opens the camera, alerting user if access is denied
   */
  let openCamera = async () => {
    let permissionResult = await ImagePicker.requestCameraPermissionsAsync();

    if (!permissionResult.granted) {
      props.makeAlert("Access Denied", "Permission to access camera is required!");
      return;
    }

    let result = await ImagePicker.launchCameraAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [4, 3],
      quality: 1,
      base64: true,
      exif: true
    });
    if (!result.cancelled && result.base64) {
      props.callback(result.base64)
      console.log(result.exif)
    }
  }

  return (
    <View style={styles.rowContainer}>
      <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
        <Image source={require('../../assets/images/selectImage.png')}
               style={[styles.buttonImage, {width: 100, height: 50}]}/>
      </TouchableOpacity>
      <TouchableOpacity onPress={openCamera} style={styles.button}>
        <Image source={require('../../assets/images/takeImage.png')}
               style={[styles.buttonImage, {width: 100, height: 50}]}/>
      </TouchableOpacity>
    </View>);
}
