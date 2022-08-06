import React, { useState, useEffect } from 'react';
import {Image, View, TouchableOpacity, StyleSheet, Text, ScrollView, Alert, Platform, StyleProp} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import DropDownPicker from 'react-native-dropdown-picker';
import Constants from "expo-constants";
import * as Sharing from 'expo-sharing';
import * as MediaLibrary from 'expo-media-library';
// import { triggerBase64Download } from 'react-base64-downloader';
import * as FileSystem from 'expo-file-system'

const { manifest } = Constants;

export default function ImagePickerExample() {
  const platform: string = Platform.OS;
  const [uri, setUri] = useState(['']);
  const [b64, setB64] = useState<string[]>([]);

  const [undone, setUndone] = useState<string[]>([]);
  const [originIndex, setOriginIndex] = useState<number[]>([]);
  const [originRedo, setOriginRedo] = useState<number[]>([]);
  const [count, setCount] = useState<number>(0);

  const [deg, setDeg] = useState('0deg');
  const [hdeg, setHDeg] = useState('0deg');
  const [vdeg, setVDeg] = useState('0deg');

  let mobileDomain: string;
  if (manifest && typeof manifest.debuggerHost === 'string'){
    mobileDomain = (typeof manifest.packagerOpts === `object`) && manifest.packagerOpts.dev
        ? manifest.debuggerHost.split(`:`)[0].concat(`:4567`)
        : `api.example.com`;
  }

  // Dropdown state
  const [openF, setOpen] = useState(false);
  const [valueF, setValue] = useState('');
  const [itemsF, setItems] = useState([
    {label: 'Invert', value: 'invert'},
    {label: 'Grayscale', value: 'gray'},
    {label: 'Blur', value: 'blur'},
    {label: 'Emojify', value: 'emoji'},
  ]);
  const [filterText, setText] = useState("NO FILTER APPLIED");

  const [openT, setOpenT] = useState(false);
  const [valueT, setValueT] = useState('');
  const [itemsT, setItemsT] = useState([
    {label: 'Rotate CCW', value: 'rotateCCW'},
    {label: 'Rotate CW', value: 'rotateCW'},
    {label: 'Flip Horizontal', value: 'hflip'},
    {label: 'Flip Vertical', value: 'vflip'},
  ])

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
      base64: true
    });
    if (!pickerResult.cancelled && pickerResult.base64) {
      setUndone([]);
      setOriginRedo([]);
      setOriginIndex([...originIndex, b64.length]);
      setB64([...b64, pickerResult.base64]);

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
      base64: true
    });
    if(!result.cancelled && result.base64){
      setUndone([]);
      setOriginRedo([]);
      setOriginIndex([...originIndex, b64.length]);
      setB64([...b64, result.base64]);

      setUri([result.uri]);
    }
  }

  async function applyFilter () {
    try{
      let domain = "localhost:4567";
      if (platform !== "web"){
        domain = mobileDomain;
      }
      console.log("http://" + domain + "/filtering?filter=" + valueF);
      console.log('data:image/jpeg;base64,' + b64[b64.length - 1]);
      let response = await fetch("http://" + domain + "/filtering?filter=" + valueF, {
        method: 'POST',
        body: b64[b64.length - 1],
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

      setB64([...b64, object.toString()]);
      setUndone([]);
      setOriginRedo([]);
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
    // no image selected
    if(b64.length === 0){
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

    // no filter selected
    if (valueF === ''){
      if (platform === 'web'){
        alert("No filter selected");
      } else {
        Alert.alert(
            "No filter selected",
            "Select a filter first",
            [ {text: "OK",} ]
        );
      }
      return;
    }

    // no dictionary :( probably a better way to do this
    let long = ""
    for (let item of itemsF){
      if (item.value === valueF){
        long = item.label
      }
    }

    if (valueF === "") {
      setText("FILTER NOT SUPPORTED");
      return;
    }
    setText(long.toUpperCase() + " FILTER APPLIED")
    applyFilter();
  }

  useEffect(() => {
    console.log("Filter applied");
    setB64(b64);
  }
  ,[b64])

  function restore() {
    setText("IMAGE RESTORED");
    setUndone([])
    setB64([...b64, b64[originIndex[originIndex.length - 1]]]);
  }

  function undo() {
    setText("ACTION UNDONE");
    setUndone([...undone, b64[b64.length - 1]])
    setB64(b64.slice(0, b64.length - 1));

    if (b64.length < originIndex[originIndex.length - 1]){
      setOriginRedo([...originRedo, originIndex[originIndex.length -1]]);
      setOriginIndex(originIndex.splice(0, originIndex.length - 1));
    }
  }

  function redo() {
    setText("ACTION REDONE")
    setB64([...b64, undone[undone.length - 1]]);
    setUndone(undone.slice(0, undone.length - 1));

    if (originRedo.length !== 0 && b64.length - 1 >= originRedo[originRedo.length - 1]){
      setOriginIndex([...originIndex, originRedo[originRedo.length -1]]);
      setOriginRedo(originRedo.splice(0, originRedo.length - 1));
    }
  }

  function original() {
    return b64.length === 0 || b64[originIndex[originIndex.length - 1]] === b64[b64.length - 1];
  }

  //Transformation functions
  function rotateCW() {
    if(deg == "270deg"){
      setDeg("0deg");
    }
    else if(deg == "0deg") {
      setDeg("90deg");
    }
    else if(deg == "90deg") {
      setDeg("180deg");
    }
    else if(deg == "180deg") {
      setDeg("270deg");
    }
  }

  function rotateCCW() {
    if(deg == "270deg"){
      setDeg("180deg");
    }
    else if(deg == "0deg") {
      setDeg("270deg");
    }
    else if(deg == "90deg") {
      setDeg("0deg");
    }
    else if(deg == "180deg") {
      setDeg("90deg");
    }
  }

  function vflip() {
    if(vdeg == "0deg"){
      setVDeg("180deg");
    } else {
      setVDeg("0deg");
    }
  }

  function hflip() {
    if(hdeg == "0deg"){
      setHDeg("180deg");
    } else {
      setHDeg("0deg");
    }
  }

  function applyTransform() {
    if(valueT == 'rotateCCW'){
      rotateCCW();
    }
    else if(valueT == 'rotateCW'){
      rotateCW();
    }
    else if(valueT == 'vflip'){
      vflip();
    }
    else if(valueT == 'hflip'){
      hflip();
    }
  }

  async function share() {
    if(Platform.OS === 'web'){
      alert('Sharing not available on web');
      return;
    }
    const temp = Image.resolveAssetSource(require('../server/image.png')).uri;
    console.log(temp);
    // await Sharing.shareAsync(uri[uri.length - 1]);
    await Sharing.shareAsync(temp);
  }

  function download() {
    if (platform == 'web'){
      if (b64.length !== 0) {
        // triggerBase64Download("data:image/jpeg;base64," + b64[b64.length - 1], 'photo_download')
        console.log("Image downloaded")
      } else {
        console.log("No image to download")
      }
    } else {
      const path = FileSystem.cacheDirectory + `download${count}.png`;
      setCount(count + 1);

      FileSystem.writeAsStringAsync(path, b64[b64.length - 1], {encoding: FileSystem.EncodingType.Base64}).then(res => {
        console.log(res);
        FileSystem.getInfoAsync(path, {size: true, md5: true}).then(file => {
          console.log("File ", file);
        })
      }).catch(err => {
        console.log("err", err);
      })

      MediaLibrary.saveToLibraryAsync(path).then(() => {
        console.log("Image saved");
      });
    }

  }

  return (
    <View style={styles.container}>
        <ScrollView showsVerticalScrollIndicator={true} nestedScrollEnabled={true}>
          <View style={styles.rowContainer}>
            <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
              <Text style={styles.buttonText}>Pick a photo</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={openCamera} style={styles.button}>
              <Text style={styles.buttonText}>Take a photo</Text>
            </TouchableOpacity>
          </View>
          <View style={styles.rowContainer}>
            <TouchableOpacity 
                onPress={download}
                style={b64.length === 0 ? styles.disabledButton : styles.button}
                disabled={b64.length === 0}>
              <Text style={styles.buttonText}>Download</Text>
            </TouchableOpacity>
            <TouchableOpacity 
                onPress={share} 
                style={b64.length === 0 ? styles.disabledButton : styles.button}
                disabled={b64.length === 0}>
              <Text style={styles.buttonText}>Share</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.imageContainer}>
            {b64[b64.length - 1] !== '' &&
                <Image source={{uri: 'data:image/jpeg;base64,' + b64[b64.length - 1]}} 
                style={[styles.image, 
                {transform: [
                  {rotate: deg}, 
                  {rotateY: hdeg},
                  {rotateX: vdeg},
                  ]}]} />}
          </View>
          
          <View style={styles.rowContainer}>
            <TouchableOpacity 
                onPress={applyTransform} 
                style={b64.length === 0 ? styles.disabledButton : [styles.button, {backgroundColor: 'darkorchid'}]}
                disabled={b64.length === 0}>
              <Text style={styles.buttonText}>Apply Transform</Text>
            </TouchableOpacity>
          </View>
          <View style={[styles.rowContainer, {zIndex: 1}]}>
            <DropDownPicker
                open={openT}
                multiple={false}
                value={valueT}
                items={itemsT}
                setOpen={setOpenT}
                setValue={setValueT}
                setItems={setItemsT}
                listMode="SCROLLVIEW"
                style={styles.button}
                textStyle={styles.dropText}
                placeholder="Select a Transform"
            />
          </View>
          
          <View style={styles.rowContainer}>
            <Text style={styles.instructions}>
              STATUS: {filterText}
            </Text>
          </View>
          <View style={[styles.rowContainer]}>
            <TouchableOpacity 
                onPress={filterSelect} 
                style={b64.length === 0 ? styles.disabledButton : [styles.button, {backgroundColor: 'darkorchid'}]}
                disabled={b64.length === 0}>
              <Text style={styles.buttonText}>Apply Filter</Text>
            </TouchableOpacity>
          </View>
          <View style={[styles.rowContainer, {zIndex: 1}]}>
            <DropDownPicker
                open={openF}
                multiple={false}
                value={valueF}
                items={itemsF}
                setOpen={setOpen}
                setValue={setValue}
                setItems={setItems}
                listMode="SCROLLVIEW"
                style={styles.button}
                textStyle={styles.dropText}
                placeholder="Select a Filter"
            />
          </View>

          <View style={styles.rowContainer}>
            <TouchableOpacity
                onPress={undo}
                style={b64.length === 0 ? styles.disabledButton : styles.button}
                disabled={b64.length === 0}>
              <Text style={styles.buttonText}>Undo</Text>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={redo}
                style={undone.length === 0 ? styles.disabledButton: styles.button }
                disabled={undone.length === 0}>
              <Text style={styles.buttonText}>Redo</Text>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={restore}
                style={original() ? styles.disabledButton : styles.button}
                disabled={original()}>
              <Text style={styles.buttonText}>Restore</Text>
            </TouchableOpacity>
          </View>
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
  disabledButton: {
    backgroundColor: 'purple',
    padding: 20,
    borderRadius: 10,
    border: '2px solid #000',
    marginBottom: 5,
    marginTop: 5,
    marginHorizontal: 2,
    flex: 1,
    opacity: 0.4,
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
    zIndex: 2,
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