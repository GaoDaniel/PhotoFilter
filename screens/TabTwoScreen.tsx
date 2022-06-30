import React, { useState } from 'react';
import {Image, View, TouchableOpacity, StyleSheet, Text, ImageSourcePropType} from 'react-native';
import * as ImagePicker from 'expo-image-picker';

export default function ImagePickerExample() {
  const [image, setImage] = useState<ImagePicker.ImageInfo | null>(null);
  const [data, setData] = useState<Uint8ClampedArray | null>(null);

  let openImagePickerAsync = async () => {
    let permissionResult = await ImagePicker.requestMediaLibraryPermissionsAsync();
  
    if (!permissionResult.granted) {
      alert("Permission to access camera roll is required!");
      return;
    }
  
    let pickerResult = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [4, 3],
      quality: 1,
    });
    if(pickerResult.cancelled){
      return;
    }

    setImage(pickerResult);
    console.log(image);
  }

  let openCamera = async () => {
    let permissionResult = await ImagePicker.requestCameraPermissionsAsync();

    if (!permissionResult.granted) {
      alert("Permission to access camera is required!");
      return;
    }

    let result = await ImagePicker.launchCameraAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [4, 3],
      quality: 1,
    });
    if(result.cancelled){
      return;
    }
    setImage(result);
    console.log(image);
  }

  function displayMatrix() {
    const w = image.width;
    const h = image.height;

    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;

    const ctx = canvas.getContext("2d");
    const img = document.createElement('img');
    img.src = image.uri;
    img.onload = () => {
      ctx.fillStyle = "red";
      ctx.drawImage(img, 0, 0, w, h);
      ctx.fillRect(10, 10, 500, 500);
      const data = ctx.getImageData(0, 0, w, h);
      setData(data.data);
    };
  }

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
        <Text style={styles.buttonText}>Pick a photo</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={openCamera} style={styles.button}>
        <Text style={styles.buttonText}>Take a photo</Text>
      </TouchableOpacity>
      {image && <Image source={{ uri: image.uri }} style={styles.image} />}
      {image && displayMatrix()}
      <Image source={require("../test-image.png")} style={styles.image} />
    </View>
  );
}
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  logo: {
    width: 305,
    height: 159,
    marginBottom: 20,
  },
  instructions: {
    color: '#888',
    fontSize: 18,
    marginHorizontal: 15,
    marginBottom: 10,
  },
  button: {
    backgroundColor: "blue",
    padding: 20,
    borderRadius: 5,
  },
  buttonText: {
    fontSize: 20,
    color: '#fff',
  }, 
  image: {
    width: 300,
    height: 300,
    resizeMode: "contain",
  }
});