import React, { createContext, useState, useEffect } from 'react';
import {Image, View, TouchableOpacity, StyleSheet, Text, ImageSourcePropType, ScrollView} from 'react-native';
import * as ImagePicker from 'expo-image-picker';

export default function ImagePickerExample() {
  const [image, setImage] = useState<ImagePicker.ImageInfo | null>(null);
  const [data, setData] = useState<Uint8ClampedArray | null>(null);
  const [imageData, setImageData] = useState<ImageData | null>(null);

  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext("2d");
  const canvasRef = React.useRef<HTMLCanvasElement>(null);
  const [context, setContext] = React.useState<CanvasRenderingContext2D | null>(null);

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

    
    canvas.width = w;
    canvas.height = h;
    console.log(w + ' ' + h);

    const img = document.createElement('img');
    img.src = image.uri;
    img.onload = () => {
      ctx.fillStyle = "red";
      ctx.drawImage(img, 0, 0, w, h);
      ctx.fillRect(10, 10, 500, 500);
      const tempData = ctx.getImageData(0, 0, w, h);
      setData(tempData.data);
      setImageData(tempData);
      console.log(tempData);
    };
  }

  function backToImage() {
    if (canvasRef.current) {
      const renderCtx = canvasRef.current.getContext('2d');

      if (renderCtx) {
        setContext(renderCtx);
      }
    }
    if(image && imageData){
      context?.putImageData(imageData, 0, 0);
    }
  }

  return (
    <View style={styles.container}>
    < ScrollView>
      <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
        <Text style={styles.buttonText}>Pick a photo</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={openCamera} style={styles.button}>
        <Text style={styles.buttonText}>Take a photo</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={displayMatrix} style={styles.button}>
        <Text style={styles.buttonText}>Display Matrix</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={backToImage} style={styles.button}>
        <Text style={styles.buttonText}>Back to Image</Text>
      </TouchableOpacity>
      {image && <Image source={{ uri: image.uri }} style={styles.image} />}
      <div>
      <canvas
        id="canvas"
        ref={canvasRef}
        width={image?.width}
        height={image?.height}
        style={{
          border: '2px solid #000',
          marginTop: 10,
        }}
      ></canvas>
      </div>
    </ScrollView>
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