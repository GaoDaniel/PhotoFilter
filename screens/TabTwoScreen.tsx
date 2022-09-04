import React, { useState, useEffect } from 'react';
import {Image, View, TouchableOpacity, StyleSheet, Text, ScrollView, Alert, Platform} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import DropDownPicker from 'react-native-dropdown-picker';
import Constants from "expo-constants";
import * as Sharing from 'expo-sharing';
import * as MediaLibrary from 'expo-media-library';
import * as FileSystem from 'expo-file-system';
// import { triggerBase64Download } from 'react-base64-downloader';
import * as Progress from 'react-native-progress';
import * as ImageManipulator from 'expo-image-manipulator';
import { FlipType } from 'expo-image-manipulator';
import uploadToAnonymousFilesAsync from 'anonymous-files';
import Slider from '@react-native-community/slider';

const { manifest } = Constants;

export default function ImagePickerExample() {
  const platform: string = Platform.OS;
  const [loadingSave, setLoadingSave] = useState<boolean>(false);
  const [loadingFilter, setLoadingFilter] = useState<boolean>(false);

  const [b64, setB64] = useState<string[]>([]);
  const [undone, setUndone] = useState<string[]>([]);
  const [originIndex, setOriginIndex] = useState<number[]>([]);
  const [originRedo, setOriginRedo] = useState<number[]>([]);

  // get domain of spark server (needed for connection from devices not running Spark server)
  let mobileDomain: string;
  if (manifest && typeof manifest.debuggerHost === 'string'){
    mobileDomain = (typeof manifest.packagerOpts === `object`) && manifest.packagerOpts.dev
        ? manifest.debuggerHost.split(`:`)[0].concat(`:4567`)
        : `api.example.com`;
  }

  // Filter state
  const [openF, setOpen] = useState(false);
  const [valueF, setValue] = useState('');
  const [itemsF, setItems] = useState([
    {label: 'Colors', value: 'colors'},
    {label: 'Invert', value: 'invert', parent: 'colors'},
    {label: 'Grayscale', value: 'gray', parent: 'colors'},
    {label: 'Black and White', value: 'bw', parent: 'colors'},
    {label: 'Red', value: 'red', parent: 'colors'},
    {label: 'Green', value: 'green', parent: 'colors'},
    {label: 'Blue', value: 'blue', parent: 'colors'},

    {label: 'Funny', value: 'funny'},
    {label: 'Emojify', value: 'emoji', parent: 'funny'},
    {label: 'Asciify', value: 'ascii', parent: 'funny'},
    {label: 'Ansify', value: 'ansi', parent:'funny'},

    {label: 'Classic', value: 'classic'},
    {label: 'Box Blur', value: 'box', parent: 'classic'},
    {label: 'Gaussian Blur', value: 'gauss', parent: 'classic'},
    {label: 'Sharpen', value: 'sharp', parent: 'classic'},
    {label: 'Remove Noise', value: 'noise', parent: 'classic'},
    {label: 'Brightness', value: 'bright', parent: 'classic'},
    {label: 'Saturation', value: 'sat', parent: 'classic'},
    {label: 'Outline', value: 'outline', parent: 'classic'},

    {label: 'Test', value: 'test'},
    {label: 'Test1', value: 'test1', parent: 'test'},
    {label: 'Test2', value: 'test2', parent: 'test'},
    {label: 'Test3', value: 'test3', parent: 'test'},
  ]);

  const sliderFilters : Set<String> = new Set<String>(['box', 'gauss', 'sharp', 'bright', 'sat', 'red', 'green', 'blue', 'test1', 'test2', 'test3']);
  const zto100Filters : Set<String> = new Set<String>(['box', 'gauss', 'sharp', 'test1', 'test2', 'test3']);

  // Slider state
  const [valueS, setValueS] = useState(0);



  /**
   * Opens the camera roll on the device, alerting user if access is denied
   */
  let openImagePickerAsync = async () => {
    let permissionResult = await ImagePicker.requestMediaLibraryPermissionsAsync();

    if (!permissionResult.granted) {
      makeAlert("Access Denied", "Permission to access camera roll is required!");
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
      setOriginIndex([...originIndex, b64.length]);
      setImage(pickerResult.base64);
      console.log("exif " + pickerResult.exif);
    }
  }

  /**
   * Opens the camera, alerting user if access is denied
   */
  let openCamera = async () => {
    let permissionResult = await ImagePicker.requestCameraPermissionsAsync();

    if (!permissionResult.granted) {
      makeAlert("Access Denied", "Permission to access camera is required!");
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
    if(!result.cancelled && result.base64){
      setOriginIndex([...originIndex, b64.length]);
      setImage(result.base64);
      console.log(result.exif)
    }
  }

  /**
   * applies filter by calling Spark server
   */
  async function applyFilter() {
    setLoadingFilter(true);
    try{
      let domain = platform !== "web" ? mobileDomain : "localhost:4567";
      let response = await fetch("http://" + domain + "/filtering?filter=" + valueF + "&int=" + valueS, {
        method: 'POST',
        body: b64[b64.length - 1],
      });

      if(!response.ok) {
        makeAlert("Status Wrong!", "The status is wrong! Expected: 200, was: " + response.status);
        setLoadingFilter(false);
        return;
      }

      let object = await response.json();
      setImage(object.toString());
    } catch(e){
      makeAlert("Server Error", "There was an error contacting the server", )
      console.log(e);
    }
    setLoadingFilter(false);
  }

  /**
   * Transforms image
   */
  async function applyTransform(valueT : string) {
    setLoadingFilter(true);
    let result = null;
    const uri = 'data:image/jpeg;base64,' + b64[b64.length - 1];
    if(valueT == 'rotateCCW'){
      result = await ImageManipulator.manipulateAsync(uri,[ {rotate: -90} ],{base64: true});
    }
    else if(valueT == 'rotateCW'){
      result = await ImageManipulator.manipulateAsync(uri,[ {rotate: 90} ],{base64: true});
    }
    else if(valueT == 'vflip'){
      result = await ImageManipulator.manipulateAsync(uri,[ {flip: FlipType.Vertical} ],{base64: true});
    }
    else if(valueT == 'hflip'){
      result = await ImageManipulator.manipulateAsync(uri,[ {flip: FlipType.Horizontal} ],{base64: true});
    }
    if(result && result.base64){
      setB64([...b64, result.base64]);
      setUndone([]);
      setOriginRedo([]);
    }
    setLoadingFilter(false);
  }

  /**
   * restores the image to its original, based on its location in the b64 stack
   * restore is treated like a filter, so it also clears the redo stacks
   */
  function restore() {
    setImage(b64[originIndex[originIndex.length - 1]]);
  }

  /**
   * undoes last action, moving the latest image to the top of the undone stack
   */
  function undo() {
    if (b64.length - 1 === originIndex[originIndex.length - 1]){
      setOriginRedo([...originRedo, originIndex[originIndex.length -1]]);
      setOriginIndex(originIndex.slice(0, originIndex.length - 1));
    }
    setUndone([...undone, b64[b64.length - 1]])
    setB64(b64.slice(0, b64.length - 1));
  }

  /**
   * redoes last action, moving the last undone image to the top of the b64 stack
   */
  function redo() {
    if (originRedo.length !== 0 && b64.length >= originRedo[originRedo.length - 1]){
      setOriginIndex([...originIndex, originRedo[originRedo.length -1]]);
      setOriginRedo(originRedo.slice(0, originRedo.length - 1));
    }
    setB64([...b64, undone[undone.length - 1]]);
    setUndone(undone.slice(0, undone.length - 1));
  }

  /**
   * shares the image
   * web is not supported
   */
  async function share() {
    if(!(await Sharing.isAvailableAsync())){
      // TODO: something about ssl certificates and anonymousfiles on firefox
      let remoteUri = await uploadToAnonymousFilesAsync('data:image/jpeg;base64,' + b64[b64.length - 1]);
      makeAlert('File shared',`Image shared at ${remoteUri}`);
    } else {
      const path: string = await save();
      await Sharing.shareAsync(path).then(() => {
        console.log("Image shared");
      });
    }
  }

  /**
   * downloads image
   */
  async function download() {
    if (platform == 'web'){
      let link = document.createElement("a");
      link.download = "download.png";
      link.href = 'data:image/jpeg;base64,' + b64[b64.length - 1];
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      console.log("Image downloaded - web")
    } else {
      const path: string = await save();
      MediaLibrary.saveToLibraryAsync(path).then(() => {
        console.log("Image downloaded - mobile");
      });
    }
  }

  // helper method that saves to Expo file system
  async function save() {
    setLoadingSave(true);
    const path: string = FileSystem.cacheDirectory + `download${b64.length - 1}.png`
    await FileSystem.writeAsStringAsync(
        path,
        b64[b64.length - 1],
        {encoding: FileSystem.EncodingType.Base64}
    ).then(res => {
      console.log(res);
      FileSystem.getInfoAsync(path, {size: true, md5: true}).then(file => {
        console.log("File ", file);
      })
    }).catch(err => {
      console.log("err", err);
    });
    setLoadingSave(false);
    return path;
  }

  // helper image setter method
  function setImage(uri: string) {
    setB64([...b64, uri]);
    setUndone([]);
    setOriginRedo([]);
  }

  // helper alert method
  function makeAlert(title: string, message: string){
    if (platform === 'web') {
      alert(message);
    } else {
      Alert.alert( title, message,[{ text: "OK" }] );
    }
  }

  // updates b64 main image
  useEffect(() => {
    console.log("Image changed");
    setB64(b64);
  },[b64])

  useEffect(() => {
    console.log("Filter changed");
    setValueS(0);
  },[valueF])

  return (
    <View style={styles.container}>
        <ScrollView showsVerticalScrollIndicator={true} nestedScrollEnabled={true}>
          <View style={styles.rowContainer}>
            <TouchableOpacity onPress={openImagePickerAsync} style={styles.button}>
              <Image source={require('../assets/images/selectImage.png')} style={[styles.buttonImage, {width: 100, height: 50}]}/>
            </TouchableOpacity>
            <TouchableOpacity onPress={openCamera} style={styles.button}>
              <Image source={require('../assets/images/takeImage.png')} style={[styles.buttonImage, {width: 100, height: 50}]}/>
            </TouchableOpacity>
          </View>
          <View style={styles.rowContainer}>
            <TouchableOpacity 
                onPress={download}
                style={b64.length === 0 || loadingSave ? styles.disabledButton : styles.button}
                disabled={b64.length === 0 || loadingSave}>
              <Image source={require('../assets/images/download.png')} style={styles.buttonImage}/>
            </TouchableOpacity>
            <TouchableOpacity 
                onPress={share}
                style={b64.length === 0 || loadingSave ? styles.disabledButton : styles.button}
                disabled={b64.length === 0 || loadingSave}>
              <Image source={require('../assets/images/share.png')} style={styles.buttonImage}/>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={undo}
                style={b64.length === 0 ? styles.disabledButton : styles.button}
                disabled={b64.length === 0}>
              <Image source={require('../assets/images/undo.png')} style={styles.buttonImage}/>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={redo}
                style={undone.length === 0 ? styles.disabledButton: styles.button }
                disabled={undone.length === 0}>
              <Image source={require('../assets/images/redo.png')} style={styles.buttonImage}/>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={restore}
                style={b64.length === 0 || b64[originIndex[originIndex.length - 1]] === b64[b64.length - 1] ?
                    styles.disabledButton : styles.button}
                disabled={b64.length === 0 || b64[originIndex[originIndex.length - 1]] === b64[b64.length - 1]}>
              <Image source={require('../assets/images/restore.png')} style={styles.buttonImage}/>
            </TouchableOpacity>
          </View>
          <View style={styles.rowContainer}>
            {loadingSave && <Progress.Bar width={300} indeterminate={loadingSave}/>}
          </View>

          <View style={styles.imageContainer}>
            {b64[b64.length - 1] !== '' &&
                <Image source={{uri: 'data:image/jpeg;base64,' + b64[b64.length - 1]}} 
                style={[styles.image]} />}
          </View>

          <View style={styles.rowContainer}>
            <TouchableOpacity
                onPress={() => {applyTransform('rotateCCW')}}
                style={b64.length === 0 && loadingFilter? styles.disabledButton : styles.button}
                disabled={b64.length === 0 && loadingFilter}>
              <Image source={require('../assets/images/rotateCCW.jpg')} style={styles.buttonImage}/>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={() => {applyTransform('rotateCW')}}
                style={b64.length === 0 && loadingFilter? styles.disabledButton : styles.button}
                disabled={b64.length === 0 && loadingFilter}>
              <Image source={require('../assets/images/rotateCW.jpg')} style={styles.buttonImage}/>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={() => {applyTransform('vflip')}}
                style={b64.length === 0 && loadingFilter? styles.disabledButton : styles.button}
                disabled={b64.length === 0 && loadingFilter}>
              <Image source={require('../assets/images/vflip.jpg')} style={styles.buttonImage}/>
            </TouchableOpacity>
            <TouchableOpacity
                onPress={() => {applyTransform('hflip')}}
                style={b64.length === 0 && loadingFilter? styles.disabledButton: styles.button }
                disabled={b64.length === 0 && loadingFilter}>
              <Image source={require('../assets/images/hflip.jpg')} style={styles.buttonImage}/>
            </TouchableOpacity>
          </View>

          <View style={[styles.rowContainer]}>
            <TouchableOpacity 
                onPress={applyFilter}
                style={b64.length === 0 || valueF === '' || loadingFilter ?
                    styles.disabledButton : [styles.button, {backgroundColor: 'darkorchid'}]}
                disabled={b64.length === 0 || valueF === '' || loadingFilter}>
              <Text style={styles.buttonText}>Apply Filter</Text>
            </TouchableOpacity>
          </View>
          <View style={styles.rowContainer}>
            {loadingFilter && <Progress.Bar width={300} indeterminate={loadingFilter}/>}
          </View>
          {sliderFilters.has(valueF) &&
              <Slider style={styles.button}
                minimumValue={zto100Filters.has(valueF) ? 0 : -100}
                maximumValue={100}
                step={1}
                value={valueS}
                onValueChange={setValueS}
                minimumTrackTintColor="#000000"
                maximumTrackTintColor="#000000"
                thumbTintColor='magenta'
                disabled={b64.length === 0 || valueF === '' || loadingFilter}
                onSlidingComplete={setValueS}
                tapToSeek={true}
          />}

          <View style={[styles.rowContainer, {zIndex: 1,}]}>
            <DropDownPicker
                open={openF}
                multiple={false}
                value={valueF}
                items={itemsF}
                setOpen={setOpen}
                setValue={setValue}
                setItems={setItems}

                placeholder="Select a Filter"
                listMode="SCROLLVIEW"
                autoScroll={true}
                style={[styles.button, {flexDirection: 'row'}]}
                textStyle={styles.dropText}
                dropDownContainerStyle={{ backgroundColor: "#fff", maxHeight: 500}}
                stickyHeader={true}
                categorySelectable={false}
                listItemLabelStyle={{ color: "#000" }}
                listParentLabelStyle={styles.dropdownParent}
                listChildContainerStyle={styles.dropdownItem}
                listChildLabelStyle={styles.dropdownText}
                selectedItemLabelStyle={{ fontWeight: "bold", color: '#fff' }}
                selectedItemContainerStyle={{ backgroundColor: "darkorchid" }}
                tickIconStyle={{ width: 20, height: 20}}
            />
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
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dropdownParent: {
    fontSize: 30,
    fontWeight: 'bold',
    color: 'purple',
    backgroundColor: '#fff',
    flex: 1,
    marginLeft: 20,
  },
  dropdownItem: {
    backgroundColor: 'purple',
    padding: 10,
    borderRadius: 10,
    border: '2px solid #000',
    marginVertical: 2,
    marginLeft: 20,
    flex: 1,
    // height: 15,
    width: 300,
    flexDirection: 'row',
  },
  dropdownText: {
    fontSize: 15,
    color: '#fff',
    flex: 1,
    paddingLeft: -20,
    backgroundColor: '#ffffff00'
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
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: {
    fontSize: 20,
    color: '#fff',
    flex: 1
  },
  buttonImage: {
    width: 20,
    height: 20,
    resizeMode: 'contain',
    alignItems: 'center',
    justifyContent: 'center',
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
    backgroundColor: 'purple',
    zIndex: 3
  },
})