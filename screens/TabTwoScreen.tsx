import React, { useState, useEffect } from 'react';
import {Image, View, TouchableOpacity, StyleSheet, Text, ScrollView, Alert, Platform} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import DropDownPicker from 'react-native-dropdown-picker';
import Emoji from '../tools/Emoji'

export default function ImagePickerExample() {
  const platform: string = Platform.OS;
  const [uri, setUri] = useState(['']);
  const [index, setIndex] = useState(0);

  // Dropdown state
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState('revert');
  const [items, setItems] = useState([
    {label: 'Invert', value: 'invert'},
    {label: 'Grayscale', value: 'gray'},
    {label: 'Blur', value: 'blur'},
    {label: 'Emojify', value: 'emoji'},
    {label: 'Flip Horizontal', value: 'hflip'},
    {label: 'Flip Vertical', value: 'vflip'},
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
  map.set([0, 128, 128], <Emoji symbol={0x1F30E} label={'globe showing Americas'}/>); // blue-green
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
      setIndex(0);
      setUri([pickerResult.uri]);
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
      setIndex(0);
      setUri([result.uri]);
    }
  }

  async function applyFilter () {
    try{
      let response = await fetch("http://localhost:4567/filtering?filter=" + value, {
        method: 'POST',
        body: uri[index],
      });
      if(!response.ok){
        if (platform === 'web') {
          alert("The status is wrong! Expected: 200, was: " + response.status);
        } else {
          Alert.alert(
              "Status Wrong!",
              "The status is wrong! Expected: 200, was: " + response.status,
              [{ text: "OK" }]
          );
        }
        return;
      }
      let object = await response.json();
      setIndex(uri.length)
      setUri([...uri, object.toString()]);
      console.log(object.toString());
    } catch(e){
      if (platform === 'web') {
        alert("There was an error contacting the server");
      } else {
        Alert.alert(
            "Server Error",
            "There was an error contacting the server",
            [{ text: "OK" }]
        );
      }
      console.log(e);
    }
  }

  function filterSelect() {
    if(uri[0] === ''){
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

    // no dictionary :( probably a better way to do this
    let long = ""
    for (let item of items){
      if (item.value === value){
        long = item.label
      }
    }

    if (value === "") {
      setText("FILTER NOT SUPPORTED");
      return;
    }

    setText(long.toUpperCase() + " FILTER APPLIED")
    if (value === "emoji"){
      // TODO: SparkServer separate request that returns pixel data to work with
    } else {
      applyFilter();
    }
  }

  useEffect(() => {
    console.log("Filter applied");
    setUri(uri);
  }
  ,[uri])

  function restore() {
    if(uri[0] !== ''){
      setText("IMAGE RESTORED");
      setIndex(uri.length)
      setUri([...uri, uri[0]]);
    }
  }

  function undo() {
    if(uri[0] !== '' && index > 0){
      setText("ACTION UNDONE");
      setIndex(index - 1)
      setUri(uri.slice(0, uri.length - 1))
    }
  }

  return (
    <View style={styles.container}>
      <ScrollView horizontal={true} minimumZoomScale={0.5} maximumZoomScale={2} pinchGestureEnabled={true} showsHorizontalScrollIndicator={true}>
        <ScrollView minimumZoomScale={0.5} maximumZoomScale={2} pinchGestureEnabled={true} showsVerticalScrollIndicator={true}>

          <View style={styles.rowContainer}>
            <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
              <Text style={styles.buttonText}>Pick a photo</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={openCamera} style={styles.button}>
              <Text style={styles.buttonText}>Take a photo</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.imageContainer}>
            {uri[index] !== '' && <Image source={{uri: uri[index]}} style={styles.image} />}
          </View>
          
          <View style={styles.rowContainer}>
            <TouchableOpacity onPress={filterSelect} style={
              {backgroundColor: "darkorchid",  
              padding: 20,
              borderRadius: 10,
              marginBottom: 5,
              marginTop: 5,
              marginHorizontal: 2,
              borderStyle: 'solid',
              borderColor: 'black',
              borderWidth: 2,
              flex: 2}}>
              <Text style={styles.buttonText}>Apply Filter</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.rowContainer}>
            <Text style={styles.instructions}>
              STATUS: {filterText}
            </Text>
          </View>

          <View style={styles.rowContainer}>
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
                placeholder="Select a Filter"
            />
          </View>

          <View style={styles.rowContainer}>
            <TouchableOpacity onPress={undo} style={styles.button}>
              <Text style={styles.buttonText}>Undo</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={restore} style={styles.button}>
              <Text style={styles.buttonText}>Restore</Text>
            </TouchableOpacity>
          </View>
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
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  logo: {
    width: 305,
    height: 159,
    marginBottom: 20,
  },
  instructions: {
    color: 'gray',
    fontSize: 18,
    marginHorizontal: 15,
    marginBottom: 10,
  },
  button: {
    backgroundColor: 'purple',
    padding: 20,
    borderRadius: 10,
    border: '2px solid #000',
    marginBottom: 5,
    marginTop: 5,
    marginHorizontal: 2,
    flex: 1
  },
  buttonText: {
    fontSize: 20,
    color: '#fff',
    flex: 1
  },
  image: {
    width: 300,
    height: 300,
    resizeMode: "contain",
  },
  imageContainer: {
    width: 350,
    height: 350,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'white',
    borderRadius: 10,
    
  },
  dropText: {
    fontSize: 20,
    color: 'white',
    backgroundColor: 'purple'
  },
});