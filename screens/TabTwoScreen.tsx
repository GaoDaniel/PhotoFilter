import React, { useState } from 'react';
import { Image, View, TouchableOpacity, StyleSheet, Text } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import PixelColor from 'react-native-pixel-color';
import imageToRgbaMatrix from 'image-to-rgba-matrix';

export default function ImagePickerExample() {
  const [image, setImage] = useState(null);

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

    setImage({localUri: pickerResult.uri});
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
    setImage({localUri: result.uri});
    console.log(image);
  }

  function displayMatrix() {
    console.log(image.localUri);

    imageToRgbaMatrix(image.localUri).then(console.log);

    // console.log(typeof(image.localUri));
    // for (let i = 0; i < 10; i++){
    //   for (let j = 0; j < 10; j++){
    //     PixelColor.getHex(image.localUri, {x:1, y:2}).then((color) => {
    //       console.log(color);
    //     }).catch((err: Error) => {
    //       console.log("file not found: " + err);
    //     });
    //   }
    // }

    // const img = new Image({source: image.localUri});
    // const w = image.width;
    // const h = image.height;
    //
    // console.log(w + " " + h);
    //
    // const canvas = document.createElement('canvas');
    // canvas.width = w;
    // canvas.height = h;
    //
    // const ctx = canvas.getContext('2d');
    // if (ctx != null) {
    //   ctx.drawImage(image, 0, 0);
    //   const data = ctx.getImageData(0, 0, w, h);
    //   return getPixels(data);
    // }
    // return <Text/>;

  }

  function getPixels(imgData: ImageData) {
    // get colors rgba (4 pix sequentially)
    let count = 1;
    let msg = '';
    for (let i = 0; i < imgData.data.length; i += 4) {
      msg += "\npixel red " + count + ": " + imgData.data[i];
      msg += "\npixel green " + count + ": " + imgData.data[i+1];
      msg += "\npixel blue " + count + ": " + imgData.data[i+2];
      msg += "\npixel alpha " + count + ": " + imgData.data[i+3] + "\n";
      count++;
    }
    return (
        <Text>{msg}</Text>
    );
  }

  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
      <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
        <Text style={styles.buttonText}>Pick a photo</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={openCamera} style={styles.button}>
        <Text style={styles.buttonText}>Take a photo</Text>
      </TouchableOpacity>
      {image && <Image source={{ uri: image.localUri }} style={styles.image} />}
      {image && displayMatrix()}
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