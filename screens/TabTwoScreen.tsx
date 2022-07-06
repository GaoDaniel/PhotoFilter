import React, { createContext, useState, useEffect } from 'react';
import {Image, View, TouchableOpacity, StyleSheet, Text, ImageSourcePropType, ScrollView} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import DropDownPicker from 'react-native-dropdown-picker';

export default function ImagePickerExample() {
  const [image, setImage] = useState<ImagePicker.ImageInfo | null>(null);
  const [imageData, setImageData] = useState<ImageData | null>(null);

  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext("2d");
  const canvasRef = React.useRef<HTMLCanvasElement>(null);
  const [context, setContext] = React.useState<CanvasRenderingContext2D | null>(null);

  // Dropdown things
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState(null);
  const [items, setItems] = useState([
    {label: 'Filter1', value: 'filter1'},
    {label: 'Filter2', value: 'filter2'}
  ]);
  const [filter, setFilter] = useState('');

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
    if(!pickerResult.cancelled){
      setImage(pickerResult);
      console.log(image);
    }
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
    if(!result.cancelled){
      setImage(result);
      console.log(image);
    }
  }

  function getMatrix() {
    if (image) {
      const w = image.width;
      const h = image.height;

      canvas.width = w;
      canvas.height = h;
      console.log(w + ' ' + h);

      const img = document.createElement('img');
      img.src = image.uri;
      img.onload = () => {
        if (ctx) {
          ctx.drawImage(img, 0, 0, w, h);
          const tempData = ctx.getImageData(0, 0, w, h);
          setImageData(tempData);
          console.log(tempData);
        }
      };
    }
  }

  function invertImage(){
    if (imageData){
      let newData = new Uint8ClampedArray(imageData.data.length);
      for (let i = 0; i < imageData.data.length; i+=4){
        newData[i] = 255 - imageData.data[i];
        newData[i+1] = 255 - imageData.data[i+1];
        newData[i+2] = 255 - imageData.data[i+2];
        // keep the A opacity value the same
        newData[i+3] = imageData.data[i+3];
      }
      let newImage = new ImageData(newData, imageData.width, imageData.height);
      setImageData(newImage);
      console.log(newImage);

    }
  }

  // equivalent of componentDidUpdate()
  // updates image in our canvas (used to be Back To Image)
  useEffect(() => {
    console.log("useEffect called");
    if (canvasRef.current) {
      const renderCtx = canvasRef.current.getContext('2d');

      if (renderCtx) {
        setContext(renderCtx);
      }
    }
    if(image && imageData){
      context?.putImageData(imageData, 0, 0);
    }
  }, [imageData]);

  return (
    <View style={styles.container}>
      <ScrollView horizontal={true} minimumZoomScale={0.5} maximumZoomScale={2} pinchGestureEnabled={true} showsHorizontalScrollIndicator={true}>
        <ScrollView minimumZoomScale={0.5} maximumZoomScale={2} pinchGestureEnabled={true} showsVerticalScrollIndicator={true}>
          <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
            <Text style={styles.buttonText}>Pick a photo</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={openCamera} style={styles.button}>
            <Text style={styles.buttonText}>Take a photo</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={getMatrix} style={styles.button}>
            <Text style={styles.buttonText}>Display Matrix</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={invertImage} style={styles.button}>
            <Text style={styles.buttonText}>Filter</Text>
          </TouchableOpacity>
          {image && <Image source={{ uri: image.uri }} style={styles.image} />}
          <View style={{height: 500, width: 500}}>
            <ScrollView maximumZoomScale={3} minimumZoomScale={0.05} pinchGestureEnabled={true} showsVerticalScrollIndicator={true}>
              <ScrollView horizontal={true} maximumZoomScale={3} minimumZoomScale={0.05} pinchGestureEnabled={true} showsHorizontalScrollIndicator={true}>
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
                  />
                </div>
              </ScrollView>
            </ScrollView>
          </View>
          <DropDownPicker
            open={open}
            multiple={false}
            value={value}
            items={items}
            setOpen={setOpen}
            setValue={setValue}
            setItems={setItems}
            style={styles.button}
            textStyle={styles.dropText}
          />
          <Text>
            Currently selected filter = {value}
          </Text>
        </ScrollView>
      </ScrollView>

    </View>
  );
}
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'pink',
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
    border: '2px solid #000',
    marginBottom: 5,
    marginTop: 5,
  },
  buttonText: {
    fontSize: 20,
    color: '#fff',
  }, 
  image: {
    width: 300,
    height: 300,
    resizeMode: "contain",
  },
  dropText: {
    fontSize: 20,
    color: 'white',
    backgroundColor: 'blue'
  }, 
});