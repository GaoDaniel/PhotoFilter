import React, {useState} from 'react';
import {Image, View, StyleSheet, ScrollView, Alert, Platform} from 'react-native';

import ImageUpload from './MainFunctionality/ImageUpload';
import Options from "./MainFunctionality/Options";
import Transform from "./MainFunctionality/Transform";
import Filter from "./MainFunctionality/Filter";

export default function PhotoFilter() {
  const platform: string = Platform.OS;

  // stack of b64 images
  const [b64, setB64] = useState<string[]>([]);
  const [undone, setUndone] = useState<string[]>([]);
  const [originIndex, setOriginIndex] = useState<number[]>([]);
  const [originRedo, setOriginRedo] = useState<number[]>([]);

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
    if (b64.length - 1 === originIndex[originIndex.length - 1]) {
      setOriginRedo([...originRedo, originIndex[originIndex.length - 1]]);
      setOriginIndex(originIndex.slice(0, originIndex.length - 1));
    }
    setUndone([...undone, b64[b64.length - 1]])
    setB64(b64.slice(0, b64.length - 1));
  }

  /**
   * redoes last action, moving the last undone image to the top of the b64 stack
   */
  function redo() {
    if (originRedo.length !== 0 && b64.length >= originRedo[originRedo.length - 1]) {
      setOriginIndex([...originIndex, originRedo[originRedo.length - 1]]);
      setOriginRedo(originRedo.slice(0, originRedo.length - 1));
    }
    setB64([...b64, undone[undone.length - 1]]);
    setUndone(undone.slice(0, undone.length - 1));
  }

  // helper image upload method
  function ImageUploadCallback(b64Result: string) {
    setOriginIndex([...originIndex, b64.length]);
    setImage(b64Result);
  }

  // helper image setter method
  function setImage(uri: string) {
    setB64([...b64, uri]);
    setUndone([]);
    setOriginRedo([]);
  }

  // helper alert method
  function makeAlert(title: string, message: string) {
    if (platform === 'web') {
      alert(message);
    } else {
      Alert.alert(title, message, [{text: "OK"}]);
    }
  }

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={true} nestedScrollEnabled={true}>
        <ImageUpload makeAlert={makeAlert} callback={ImageUploadCallback}/>
        <Options makeAlert={makeAlert} undo={undo} redo={redo} restore={restore}
                 disSave={b64.length === 0} disUndo={b64.length === 0} disRedo={undone.length === 0}
                 disRestore={b64.length === 0 || b64[originIndex[originIndex.length - 1]] === b64[b64.length - 1]}
                 curr={b64[b64.length - 1]} platform={platform}/>

        <View style={styles.rowContainer}>
          <View style={styles.imageContainer}>
            {b64[b64.length - 1] !== '' &&
                <Image source={{uri: 'data:image/jpeg;base64,' + b64[b64.length - 1]}}
                       style={[styles.image]}/>}
          </View>
        </View>

        <Transform callback={setImage} disabled={b64.length === 0} curr={b64[b64.length - 1]}/>
        <Filter platform={platform} curr={b64[b64.length - 1]} callback={setImage} makeAlert={makeAlert}/>
      </ScrollView>
    </View>
  );
}

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'pink',
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'stretch',
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
    alignItems: 'stretch',
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