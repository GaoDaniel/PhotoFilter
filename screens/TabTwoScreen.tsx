import React, { createContext, useState, useEffect } from 'react';
import {Image, View, TouchableOpacity, StyleSheet, Text, ImageSourcePropType, ScrollView} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import DropDownPicker from 'react-native-dropdown-picker';
import Emoji from '../tools/Emoji'
import dataUriToBuffer from 'data-uri-to-buffer';
import Jimp from 'jimp';

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

  // map of color to emoji
  let map = new Map();
  map.set([255, 255, 255], <Emoji symbol={0x1F47A} label={'ghost'}/>); // white
  map.set([255, 255, 0], <Emoji symbol={0x1F600} label={'grinning face'}/>); // yellow
  map.set([192, 192, 192], <Emoji symbol={0x1F418} label={'elephant'}/>); // light gray
  map.set([0, 255, 255], <Emoji symbol={0x1F976} label={'cold face'}/>); // sky blue
  map.set([0, 255, 0], <Emoji symbol={0x1F438} label={'frog'}/>); // yellow green
  map.set([128, 128, 128], <Emoji symbol={0x1F311} label={'new moon'}/>); // gray
  map.set([128, 128, 0], <Emoji symbol={0x1F36F} label={'honey'}/>); // dark yellow
  map.set([255, 0, 255], <Emoji symbol={0x1F338} label={'cherry blossom'}/>); // dark pink
  map.set([0, 128, 128], <Emoji symbol={0x1F30E} label={'globe showing Americas'}/>); // blue green
  map.set([255, 0, 0], <Emoji symbol={0x1F975} label={'hot face'}/>); // red
  map.set([0, 128, 0], <Emoji symbol={0x1F922} label={'nauseated face'}/>); // green
  map.set([128, 0, 128], <Emoji symbol={0x1F47F} label={'angry face with horns'}/>); // purple
  map.set([128, 0, 0], <Emoji symbol={0x1F4A9} label={'pile of poo'}/>); // brown
  map.set([0, 0, 255], <Emoji symbol={0x1F6BE} label={'water closet'}/>); // blue
  map.set([0, 0, 128], <Emoji symbol={0x1F456} label={'jeans'}/>); // dark blue
  map.set([0, 0, 0], <Emoji symbol={0x1F4A3} label={'bomb'}/>); // black

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
      console.log(image?.uri);
      //console.log(dataUriToBuffer(image?.uri).toString);
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

  const readImage = (imageSrc: any) => {
    return Jimp.read(imageSrc);
  }
  
  const scanToRgbaMatrix = (jimpImage: { bitmap: { data: { [x: string]: any; }; width: any; height: any; }; scan: (arg0: number, arg1: number, arg2: any, arg3: any, arg4: (x: any, y: any, idx: any) => void) => void; }) => {
    const rgbaMatrix: any[] = [];
  
    const pixelHandler = (x: number, y: number, idx: number) => {
      var green = jimpImage.bitmap.data[ idx + 1 ];
      var red   = jimpImage.bitmap.data[ idx + 0 ];
      var blue  = jimpImage.bitmap.data[ idx + 2 ];
      var alpha = jimpImage.bitmap.data[ idx + 3 ];
      
      if (!rgbaMatrix[y]) {
        rgbaMatrix[y] = []
      }
      
      rgbaMatrix[y][x] = [red, green, blue, alpha];
      
    }
    jimpImage.scan(
      0,
      0,
      jimpImage.bitmap.width,
      jimpImage.bitmap.height,
      pixelHandler.bind(this)
    );
    console.log(rgbaMatrix);
    return rgbaMatrix
  }
  
  const imageToRgbaMatrix = (imageSrc: any) => {
    return readImage(imageSrc)
      .then(scanToRgbaMatrix);
  }
  async function getMatrix() {
    if (image) {
      imageToRgbaMatrix(dataUriToBuffer(image.uri))
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

  function emojifyImage(){
    let list: any[] = [];
    console.log("emojify clicked");
    if (imageData){
      for (let i = 0; i < imageData.data.length; i+=4){
        // naive way to find closest color
        let smallestVal = 255*3;
        let emoji;
        for (let [key, value] of map) {
          let tempVal = Math.abs(imageData.data[i] - key[0]) + Math.abs(imageData.data[i+1] - key[1]) + Math.abs(imageData.data[i+2] - key[2]);
          if (tempVal < smallestVal){
            smallestVal = tempVal;
            emoji = value;
          }
        }
        if ((i/4+1)%(imageData.width) !== 0){
          list.push(<div style={{float: "left"}}>{emoji}</div>);
        } else {
          list.push(<div style={{float: "right"}}>{emoji}</div>);
        }
      }
      console.log(list);
    }
    return list;
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
          <View style={{width: 500}}>
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
          <View style={{width: 500}}>
            <ScrollView maximumZoomScale={3} minimumZoomScale={0.05} pinchGestureEnabled={true} showsVerticalScrollIndicator={true}>
              <ScrollView horizontal={true} maximumZoomScale={3} minimumZoomScale={0.05} pinchGestureEnabled={true} showsHorizontalScrollIndicator={true}>
                <View style={{
                  flexDirection:'row',
                  justifyContent: 'flex-start',
                  alignItems: 'flex-start',
                  direction: 'inherit',
                  flexWrap: 'wrap',
                  width: imageData?.width*23,}}>
                  {emojifyImage()}
                </View>
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