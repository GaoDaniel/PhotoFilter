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

const { manifest } = Constants;

export default function ImagePickerExample() {
  const platform: string = Platform.OS;
  const [loadingSave, setLoadingSave] = useState<boolean>(false);
  const [loadingFilter, setLoadingFilter] = useState<boolean>(false);

  const [b64, setB64] = useState<string[]>([]);
  const [undone, setUndone] = useState<string[]>([]);
  const [originIndex, setOriginIndex] = useState<number[]>([]);
  const [originRedo, setOriginRedo] = useState<number[]>([]);

  const [deg, setDeg] = useState('0deg');
  const [hdeg, setHDeg] = useState('0deg');
  const [vdeg, setVDeg] = useState('0deg');

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
    {label: 'Invert', value: 'invert'},
    {label: 'Grayscale', value: 'gray'},
    {label: 'Blur', value: 'blur'},
    {label: 'Emojify', value: 'emoji'},
  ]);

  // Transform state
  const [openT, setOpenT] = useState(false);
  const [valueT, setValueT] = useState('');
  const [itemsT, setItemsT] = useState([
    {label: 'Rotate CCW', value: 'rotateCCW'},
    {label: 'Rotate CW', value: 'rotateCW'},
    {label: 'Flip Horizontal', value: 'hflip'},
    {label: 'Flip Vertical', value: 'vflip'},
  ])

  /**
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
    }
  }

  /**
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
    }
  }

  /**
   * Checks that the filter and image are selected before calling a request to the server
   */
  function filterSelect() {

    applyFilter().then(() => {});
  }

  /**
   * helper method that calls the filter Spark server
   */
  async function applyFilter() {
    setLoadingFilter(true);
    try{
      let domain = platform !== "web" ? mobileDomain : "localhost:4567";
      console.log("http://" + domain + "/filtering?filter=" + valueF);

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
        setLoadingFilter(false);
        return;
      }
      let object = await response.json();
      setB64([...b64, object.toString()]);
      console.log('Filtered image', 'data:image/jpeg;base64,' + object.toString());

      setUndone([]);
      setOriginRedo([]);
      setLoadingFilter(false);
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

  /**
   * restores the image to its original, based on its location in the b64 stack
   */
  function restore() {
    setUndone([])
    setB64([...b64, b64[originIndex[originIndex.length - 1]]]);
  }

  /**
   * undoes last action, moving the latest image to the top of the undone stack
   */
  function undo() {
    setUndone([...undone, b64[b64.length - 1]])
    setB64(b64.slice(0, b64.length - 1));

    if (b64.length < originIndex[originIndex.length - 1]){
      setOriginRedo([...originRedo, originIndex[originIndex.length -1]]);
      setOriginIndex(originIndex.splice(0, originIndex.length - 1));
    }
  }

  /**
   * redoes last action, moving the last undone image to the top of the b64 stack
   */
  function redo() {
    setB64([...b64, undone[undone.length - 1]]);
    setUndone(undone.slice(0, undone.length - 1));

    if (originRedo.length !== 0 && b64.length - 1 >= originRedo[originRedo.length - 1]){
      setOriginIndex([...originIndex, originRedo[originRedo.length -1]]);
      setOriginRedo(originRedo.splice(0, originRedo.length - 1));
    }
  }

  /**
   * Transforms image
   */
  function applyTransform() {
    if(valueT == 'rotateCCW'){
      const num: number = +deg.split('deg')[0];
      setDeg(String((num + 270)%360) + 'deg');
    }
    else if(valueT == 'rotateCW'){
      const num: number = +deg.split('deg')[0];
      setDeg(String((num + 90)%360) + 'deg');
    }
    else if(valueT == 'vflip'){
      const num: number = +vdeg.split('deg')[0];
      setVDeg(String((num + 180)%360) + 'deg');
    }
    else if(valueT == 'hflip'){
      const num: number = +hdeg.split('deg')[0];
      setHDeg(String((num + 180)%360) + 'deg');
    }
  }

  /**
   * Function for sharing the image
   */
  async function share() {
    if(Platform.OS === 'web'){
      alert('Sharing not available on web');
      return;
    } else {
      const path: string = FileSystem.cacheDirectory + `download${b64.length - 1}.png`;
      await save(path);
      await Sharing.shareAsync(path).then(() => {
        console.log("Image shared");
      });
    }
  }

  /**
   * function for downloading image
   */
  async function download() {
    if (platform == 'web'){
      // triggerBase64Download("data:image/jpeg;base64," + b64[b64.length - 1], 'photo_download')
      console.log("Image downloaded")
    } else {
      const path = FileSystem.cacheDirectory + `download${b64.length - 1}.png`;
      await save(path);
      MediaLibrary.saveToLibraryAsync(path).then(() => {
        console.log("Image saved");
      });
    }
  }

  // helper method that saves to Expo file system
  async function save(path: string) {
    // let domain = "localhost:4567";
    // if (platform !== "web"){
    //   domain = mobileDomain;
    // }
    // const downloadResumable = FileSystem.createDownloadResumable(
    //     "http://" + domain + "/filtering?filter=" + valueF,
    //     path,
    //     { md5: true },
    //     (downloadProgress) => {
    //       setProgress(downloadProgress.totalBytesWritten / downloadProgress.totalBytesExpectedToWrite);
    //     }
    // );
    //
    // await downloadResumable.downloadAsync();

    setLoadingSave(true);
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
    })
    setLoadingSave(false);
  }

  // updates b64 main image
  useEffect(() => {
    console.log("Image changed");
    setB64(b64);
  },[b64])

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
                style={b64.length === 0 || loadingSave ? styles.disabledButton : styles.button}
                disabled={b64.length === 0 || loadingSave}>
              <Text style={styles.buttonText}>Download</Text>
            </TouchableOpacity>
            <TouchableOpacity 
                onPress={share}
                style={b64.length === 0 || loadingSave ? styles.disabledButton : styles.button}
                disabled={b64.length === 0 || loadingSave}>
              <Text style={styles.buttonText}>Share</Text>
            </TouchableOpacity>
          </View>
          <View style={styles.rowContainer}>
            {loadingSave && <Progress.Bar width={300} indeterminate={loadingSave}/>}
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
                style={b64.length === 0 || valueT === '' ?
                    styles.disabledButton : [styles.button, {backgroundColor: 'darkorchid'}]}
                disabled={b64.length === 0 || valueT === ''}>
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

          <View style={[styles.rowContainer]}>
            <TouchableOpacity 
                onPress={filterSelect} 
                style={b64.length === 0 || valueF === '' || loadingFilter ?
                    styles.disabledButton : [styles.button, {backgroundColor: 'darkorchid'}]}
                disabled={b64.length === 0 || valueF === '' || loadingFilter}>
              <Text style={styles.buttonText}>Apply Filter</Text>
            </TouchableOpacity>
          </View>
          <View style={styles.rowContainer}>
            {loadingFilter && <Progress.Bar width={300} indeterminate={loadingFilter}/>}
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
                style={b64.length === 0 || b64[originIndex[originIndex.length - 1]] === b64[b64.length - 1] ?
                    styles.disabledButton : styles.button}
                disabled={b64.length === 0 || b64[originIndex[originIndex.length - 1]] === b64[b64.length - 1]}>
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