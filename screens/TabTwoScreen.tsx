import React, { createContext, useState, useEffect } from 'react';
import {Image, View, TouchableOpacity, StyleSheet, Text, ImageSourcePropType, ScrollView, Alert, Platform} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import DropDownPicker from 'react-native-dropdown-picker';
import Emoji from '../tools/Emoji'
import dataUriToBuffer from 'data-uri-to-buffer';
import Jimp from 'jimp';

export default function ImagePickerExample() {
  const platform: string = Platform.OS;
  const [image, setImage] = useState<ImagePicker.ImageInfo | null>(null);
  const [jimage, setJImage] = useState<Promise | null>(null);
  const [rgba, setRGBA] = useState<any[] | null>(null);

  // Dropdown things
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState(null);
  const [items, setItems] = useState([
    {label: 'Invert', value: 'invert'},
    {label: 'Emojify', value: 'emojify'}
  ]);
  const [filterText, setText] = useState("NO FILTER APPLIED");

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

  /*
   * Opens the camera roll on the device
   * Alerts user if access is denied
   */
  let openImagePickerAsync = async () => {
    let permissionResult = await ImagePicker.requestMediaLibraryPermissionsAsync();

    if (!permissionResult.granted) {
      if (platform === 'web') {
        alert("Permission to access camera roll is required!");
      } else {
        Alert.alert(
            "Access Denied",
            "Allow access to the camera roll.",
            [{ text: "OK" }]
        );
      }
      return;
    }

    let pickerResult = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsEditing: true,
      aspect: [4, 3],
      quality: 1,
    });
    if (!pickerResult.cancelled) {
      setImage(pickerResult);
      const temp = await Jimp.read(dataUriToBuffer(pickerResult.uri));
      setJImage(temp);
      console.log(image);
    }
  }

  /*
   * Opens the camera
   * Alerts user if access is denied
   */
  let openCamera = async () => {
    let permissionResult = await ImagePicker.requestCameraPermissionsAsync();

    if (!permissionResult.granted) {
      if (platform === 'web'){
        alert("Permission to access camera is required!");
      } else {
        Alert.alert(
            "Access Denied",
            "Allow access to the camera.",
            [{text: "OK"}]
        );
      }
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
      const temp = await Jimp.read(dataUriToBuffer(result.uri));
      setJImage(temp);
      console.log(image);
    }
  }

  /*
   * takes a jimp image and returns an rgbaMatrix[height][width][4]
   */
  const scanToRgbaMatrix = (jimpImage: { bitmap: { data: { [x: string]: any; };
    width: any; height: any; };
    scan: (arg0: number, arg1: number, arg2: any, arg3: any, arg4: (x: any, y: any, idx: any) => void) => void; }) => {
    const rgbaMatrix: any[] = [];

    const pixelHandler = (x: number, y: number, idx: number) => {
      const red = jimpImage.bitmap.data[ idx ];
      const green = jimpImage.bitmap.data[ idx + 1 ];
      const blue  = jimpImage.bitmap.data[ idx + 2 ];
      const alpha = jimpImage.bitmap.data[ idx + 3 ];

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
    setRGBA(rgbaMatrix);
    return rgbaMatrix;
  }

  /*
   * gets and stores RGBa matrix
   * does nothing if image has not been uploaded yet
   */
  async function getMatrix() {
    if (jimage) {
      console.log(jimage);
      scanToRgbaMatrix(jimage);
    }
  }

  /*
   * inverts the current pixels
   */
  function invertImage(){
    if (rgba){
      const newData: any[] = [];
      for (let i = 0; i < rgba.length; i++){
        for(let j = 0; j < rgba[0].length; j++){
          if (!newData[i]) {
            newData[i] = [];
          }
          newData[i][j] = [255 - rgba[i][j][0], 255 - rgba[i][j][1], 255 - rgba[i][j][2], rgba[i][j][3]]
        }
      }
      setRGBA(newData);
      console.log(rgba);
    }
  }
  /*
  // comment out for now cuz causing problems
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
  */

  // there has to be better way to do this
  function filterSelect() {
    if(!image){
      if (platform === 'web'){
        alert("No image selected");
      } else {
        Alert.alert(
            "No image selected",
            "Upload an image first",
            [
              {
                text: "Cancel",
                onPress: () => console.log("Cancel Pressed"),
                style: "cancel"
              },
              {
                text: "Upload",
                onPress: openImagePickerAsync
              },
              {
                text: "Camera",
                onPress: openCamera
              }
            ]
        );
      }
      return;
    }
    // Note: this does work, but nothing happens because we have no way to display the image from only pixel data
    // How to go from pixel data array -> base64 or uri or buffer?
    switch(value){
      case "invert":
        console.log("invertImage called");
        invertImage();
        setText("INVERT FILTER APPLIED");
        break;
      case "emojify":
        setText("EMOJIFY FILTER APPLIED");
        break;
      default:
        setText("FILTER NOT SUPPORTED");
        break;
    }
  }

  // equivalent of componentDidUpdate()
  // updates image whenever rgba changes
  useEffect(() => {
    console.log("rgba changed");
    if (jimage){
      // TODO: to be filled in after discussion
    }


  }, [rgba]);

  return (
    <View style={styles.container}>
      <ScrollView horizontal={true} minimumZoomScale={0.5} maximumZoomScale={2} pinchGestureEnabled={true} showsHorizontalScrollIndicator={true}>
        <ScrollView minimumZoomScale={0.5} maximumZoomScale={2} pinchGestureEnabled={true} showsVerticalScrollIndicator={true}>
          <View style={styles.rowContainer}>
            <Text style={{marginHorizontal: 0, marginVertical: 30, fontSize: 24, textAlign: 'center', textAlignVertical: 'center'}}> Upload a Photo: </Text>
            <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
              <Text style={styles.buttonText}>Pick a photo</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={openCamera} style={styles.button}>
              <Text style={styles.buttonText}>Take a photo</Text>
            </TouchableOpacity>
          </View>
          <TouchableOpacity onPress={getMatrix} style={styles.button}>
            <Text style={styles.buttonText}>Display Matrix</Text>
          </TouchableOpacity>

          {image && <Image source={{ uri: image.uri }} style={styles.image} />}
          <View style={{width: 500}}>
            <ScrollView maximumZoomScale={3} minimumZoomScale={0.05} pinchGestureEnabled={true} showsVerticalScrollIndicator={true}>
              <ScrollView horizontal={true} maximumZoomScale={3} minimumZoomScale={0.05} pinchGestureEnabled={true} showsHorizontalScrollIndicator={true}>
                <View style={{
                  flexDirection:'row',
                  justifyContent: 'flex-start',
                  alignItems: 'flex-start',
                  direction: 'inherit',
                  flexWrap: 'wrap',
                  width: 500}}>

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
          <TouchableOpacity onPress={filterSelect} style={styles.button}>
            <Text style={styles.buttonText}>Filter</Text>
          </TouchableOpacity>
          <Text>
            Currently selected filter = {value}
          </Text>
          <Text>
            {filterText}
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
  rowContainer: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
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